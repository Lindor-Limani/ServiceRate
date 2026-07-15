package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.model.common.exception.ConflictException;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import com.stripe.Stripe;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StripeCheckoutRequestTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private MailService mailService;
    @Mock
    private UserService userService;
    @Mock
    private StripeWebhookEventService stripeWebhookEventService;

    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> idempotencyKey = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private HttpServer stripeServer;
    private StripeConnectService stripeConnectService;
    private String originalApiBase;
    private String originalApiKey;

    @BeforeEach
    void setUp() throws IOException {
        originalApiBase = Stripe.getApiBase();
        originalApiKey = Stripe.apiKey;
        stripeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stripeServer.createContext("/v1/checkout/sessions", this::handleCheckoutRequest);
        stripeServer.start();
        Stripe.overrideApiBase("http://127.0.0.1:" + stripeServer.getAddress().getPort());

        stripeConnectService = new StripeConnectService(
                userRepository,
                bookingRepository,
                mailService,
                userService,
                stripeWebhookEventService
        );
        ReflectionTestUtils.setField(stripeConnectService, "secretKey", "sk_test_local_adapter");
        ReflectionTestUtils.setField(stripeConnectService, "currency", "eur");
        ReflectionTestUtils.setField(stripeConnectService, "checkoutSuccessUrl", "https://example.test/success");
        ReflectionTestUtils.setField(stripeConnectService, "checkoutCancelUrl", "https://example.test/cancel");
    }

    @AfterEach
    void tearDown() {
        if (stripeServer != null) {
            stripeServer.stop(0);
        }
        if (originalApiBase != null) {
            Stripe.overrideApiBase(originalApiBase);
        }
        Stripe.apiKey = originalApiKey;
    }

    @Test
    void checkoutRequestUsesStableBookingScopedIdempotencyKey() {
        Booking booking = checkoutBooking(UUID.randomUUID());

        StripeConnectService.StripeCheckout checkout =
                stripeConnectService.createCheckoutSession(booking, false);

        assertThat(requestCount).hasValue(1);
        assertThat(idempotencyKey).hasValue("servicerate-checkout-" + booking.getId());
        assertThat(checkout.sessionId()).isEqualTo("cs_local_once");
        assertThat(checkout.url()).isEqualTo("https://checkout.stripe.test/local");
        assertThat(booking.getStripePaymentIntentId()).isEqualTo("pi_local_once");
        assertThat(booking.getPaymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(booking.getStripeExpectedAmountMinor()).isEqualTo(10000L);
        assertThat(booking.getStripeExpectedApplicationFeeMinor()).isEqualTo(1100L);
        assertThat(booking.getStripeCurrencyCode()).isEqualTo("EUR");
        assertThat(booking.getStripeConnectedAccountId()).isEqualTo("acct_local");
        assertThat(requestBody.get()).contains(
                "line_items[0][price_data][unit_amount]=10000",
                "line_items[0][price_data][currency]=eur",
                "payment_intent_data[application_fee_amount]=1100",
                "payment_intent_data[transfer_data][destination]=acct_local"
        );
        verify(bookingRepository).save(booking);
    }

    @Test
    void checkoutRequestReusesCompleteSnapshotInsteadOfChangedSourceValues() {
        Booking booking = checkoutBooking(UUID.randomUUID());
        booking.setStripeExpectedAmountMinor(12345L);
        booking.setStripeExpectedApplicationFeeMinor(2345L);
        booking.setStripeCurrencyCode("USD");
        booking.setStripeConnectedAccountId("acct_snapshot");
        booking.setGrossAmount(999.0);
        booking.setPlatformFeeAmount(99.0);
        booking.getServiceOffering().getProvider().setStripeConnectedAccountId("acct_snapshot");

        stripeConnectService.createCheckoutSession(booking, false);

        assertThat(requestBody.get()).contains(
                "line_items[0][price_data][unit_amount]=12345",
                "line_items[0][price_data][currency]=usd",
                "payment_intent_data[application_fee_amount]=2345",
                "payment_intent_data[transfer_data][destination]=acct_snapshot"
        );
        assertThat(requestBody.get()).doesNotContain(
                "unit_amount]=99900", "application_fee_amount]=9900", "currency]=eur"
        );
    }

    @Test
    void checkoutRejectsIncompleteOrForeignSnapshotBeforeProviderCall() {
        Booking booking = checkoutBooking(UUID.randomUUID());
        booking.setStripeExpectedAmountMinor(10000L);

        assertThatThrownBy(() -> stripeConnectService.createCheckoutSession(booking, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Der Stripe-Checkout enthaelt keine vollstaendigen unveraenderlichen Zahlungs-Sollwerte.");

        booking.setStripeCurrencyCode("EUR");
        booking.setStripeExpectedApplicationFeeMinor(1000L);
        booking.setStripeConnectedAccountId("acct_foreign");
        assertThatThrownBy(() -> stripeConnectService.createCheckoutSession(booking, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Der gespeicherte Stripe Connected Account entspricht nicht dem verifizierten Anbieter.");

        assertThat(requestCount).hasValue(0);
    }

    @Test
    void checkoutRejectsApplicationFeeOutsideSnapshotBoundsBeforeProviderCall() {
        Booking booking = checkoutBooking(UUID.randomUUID());
        booking.setPlatformFeeAmount(-1.0);

        assertThatThrownBy(() -> stripeConnectService.createCheckoutSession(booking, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stripe Checkout erfordert eine Plattformgebuehr zwischen null und dem Buchungsbetrag.");

        booking.setPlatformFeeAmount(100.01);
        assertThatThrownBy(() -> stripeConnectService.createCheckoutSession(booking, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stripe Checkout erfordert eine Plattformgebuehr zwischen null und dem Buchungsbetrag.");

        assertThat(requestCount).hasValue(0);
    }

    @Test
    void checkoutRejectsInvalidAmountAndCurrencyBeforeProviderCall() {
        Booking booking = checkoutBooking(UUID.randomUUID());
        for (Double invalidAmount : new Double[]{null, 0.0, -1.0}) {
            booking.setGrossAmount(invalidAmount);
            assertThatThrownBy(() -> stripeConnectService.createCheckoutSession(booking, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Stripe Checkout erfordert einen positiven Buchungsbetrag.");
        }

        booking.setGrossAmount(100.0);
        ReflectionTestUtils.setField(stripeConnectService, "currency", "EURO");
        assertThatThrownBy(() -> stripeConnectService.createCheckoutSession(booking, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stripe Checkout erfordert einen gueltigen ISO-Waehrungscode.");

        assertThat(requestCount).hasValue(0);
    }

    @Test
    void checkoutWithoutPersistedBookingIdIsRejectedBeforeProviderCall() {
        Booking booking = checkoutBooking(null);

        assertThatThrownBy(() -> stripeConnectService.createCheckoutSession(booking, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stripe Checkout benötigt eine persistierte Buchung.");

        assertThat(requestCount).hasValue(0);
    }

    private Booking checkoutBooking(UUID bookingId) {
        User customer = new User();
        customer.setId(UUID.randomUUID());
        customer.setEmail("customer@example.com");
        customer.setStripeCustomerId("cus_local");

        User provider = new User();
        provider.setId(UUID.randomUUID());
        provider.setEmail("provider@example.com");
        provider.setStripeConnectedAccountId("acct_local");
        provider.setStripeOnboardingStatus("CONNECTED");

        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setProvider(provider);
        offering.setTitle("Idempotenter Checkout");

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCustomer(customer);
        booking.setServiceOffering(offering);
        booking.setGrossAmount(100.0);
        booking.setPlatformFeeAmount(11.0);
        return booking;
    }

    private void handleCheckoutRequest(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
        requestBody.set(URLDecoder.decode(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));
        byte[] response = """
                {
                  "id":"cs_local_once",
                  "object":"checkout.session",
                  "payment_intent":"pi_local_once",
                  "url":"https://checkout.stripe.test/local"
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
