package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.CreateCheckoutRequest;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class StripeCheckoutConcurrencyIntegrationTest {

    private static final int PARALLEL_REQUESTS = 10;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private StripeConnectService stripeConnectService;
    @MockitoBean
    private PayPalService payPalService;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        bookingRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        userRepository.deleteAll();
        executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void parallelReplaysCreateAndPersistExactlyOneStripeCheckout() throws Exception {
        User customer = saveUser("stripe-checkout-customer@example.com", "CUSTOMER");
        User provider = saveUser("stripe-checkout-provider@example.com", "PROVIDER");
        Booking booking = saveAcceptedBooking(customer, saveService(provider));
        CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attemptsStarted = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch firstProviderCall = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicInteger providerCalls = new AtomicInteger();

        when(stripeConnectService.createCheckoutSession(any(Booking.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    providerCalls.incrementAndGet();
                    firstProviderCall.countDown();
                    if (!releaseProvider.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Stripe-Testfreigabe fehlgeschlagen");
                    }
                    Booking changed = invocation.getArgument(0);
                    changed.setStripeCheckoutSessionId("cs_parallel_once");
                    changed.setStripePaymentIntentId("pi_parallel_once");
                    changed.setCheckoutUrl("https://checkout.stripe.test/parallel");
                    changed.setPaymentProvider("CARD");
                    changed.setPaymentStatus("CHECKOUT_CREATED");
                    changed.setSettlementStatus("STRIPE_DESTINATION_CHARGE_PENDING");
                    return new StripeConnectService.StripeCheckout(
                            changed.getStripeCheckoutSessionId(), changed.getCheckoutUrl()
                    );
                });

        List<Future<BookingResponse>> results = new ArrayList<>();
        for (int request = 0; request < PARALLEL_REQUESTS; request++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Paralleler Teststart fehlgeschlagen");
                }
                attemptsStarted.countDown();
                return bookingService.createCheckout(
                        booking.getId(), new CreateCheckoutRequest("CARD", false), customer.getEmail()
                );
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(attemptsStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(firstProviderCall.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            Thread.sleep(300);
            assertThat(providerCalls).hasValue(1);
        } finally {
            releaseProvider.countDown();
        }

        for (Future<BookingResponse> result : results) {
            BookingResponse response = result.get(20, TimeUnit.SECONDS);
            assertThat(response.stripeCheckoutSessionId()).isEqualTo("cs_parallel_once");
            assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.test/parallel");
        }

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getPaymentProvider()).isEqualTo("CARD");
        assertThat(persisted.getPaymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(persisted.getStripeCheckoutSessionId()).isEqualTo("cs_parallel_once");
        assertThat(persisted.getStripePaymentIntentId()).isEqualTo("pi_parallel_once");
        verify(stripeConnectService, times(1)).createCheckoutSession(any(Booking.class), anyBoolean());
    }

    private User saveUser(String email, String accountType) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("not-used-in-this-test");
        user.setFirstName(accountType);
        user.setLastName("User");
        user.setAccountType(accountType);
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private ServiceOffering saveService(User provider) {
        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setProvider(provider);
        offering.setTitle("Stripe Checkout Service");
        offering.setDescription("Beschreibung");
        offering.setCategory("REPAIR");
        offering.setPrice(80.0);
        offering.setStatus("ACTIVE");
        return serviceOfferingRepository.saveAndFlush(offering);
    }

    private Booking saveAcceptedBooking(User customer, ServiceOffering offering) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(offering);
        booking.setServiceDate(OffsetDateTime.now().plusDays(1));
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStatus("ACCEPTED");
        booking.setPaymentProvider("MANUAL");
        booking.setPaymentStatus("UNPAID");
        return bookingRepository.saveAndFlush(booking);
    }
}
