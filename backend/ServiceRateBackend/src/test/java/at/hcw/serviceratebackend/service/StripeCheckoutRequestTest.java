package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
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
        verify(bookingRepository).save(booking);
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
        exchange.getRequestBody().readAllBytes();
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
