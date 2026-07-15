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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PayPalCheckoutConcurrencyIntegrationTest {

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
    private PayPalService payPalService;
    @MockitoBean
    private StripeConnectService stripeConnectService;

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
    void parallelReplaysCreateAndPersistExactlyOnePayPalOrder() throws Exception {
        User customer = saveUser("paypal-checkout-customer@example.com", "CUSTOMER");
        User provider = saveUser("paypal-checkout-provider@example.com", "PROVIDER");
        provider.setPaypalMerchantId("verified-merchant");
        provider.setPaypalOnboardingStatus("CONNECTED");
        provider.setPaypalPermissionsGranted(true);
        provider.setPaypalEmailConfirmed(true);
        userRepository.saveAndFlush(provider);
        Booking booking = saveAcceptedBooking(customer, saveService(provider));
        CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attemptsStarted = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch firstProviderCall = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicInteger providerCalls = new AtomicInteger();

        when(payPalService.isProviderCheckoutEligible(any(User.class))).thenReturn(true);
        when(payPalService.createOrder(any(Booking.class))).thenAnswer(invocation -> {
            providerCalls.incrementAndGet();
            firstProviderCall.countDown();
            if (!releaseProvider.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("PayPal-Testfreigabe fehlgeschlagen");
            }
            return new PayPalService.PayPalOrder(
                    "ORDER-parallel-once", "https://paypal.example/parallel"
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
                        booking.getId(), new CreateCheckoutRequest("PAYPAL", false), customer.getEmail()
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
            assertThat(response.paypalOrderId()).isEqualTo("ORDER-parallel-once");
            assertThat(response.checkoutUrl()).isEqualTo("https://paypal.example/parallel");
        }

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getPaymentProvider()).isEqualTo("PAYPAL");
        assertThat(persisted.getPaymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(persisted.getPaypalOrderId()).isEqualTo("ORDER-parallel-once");
        assertThat(persisted.getCheckoutUrl()).isEqualTo("https://paypal.example/parallel");
        assertThat(persisted.getPaypalExpectedAmount()).isEqualByComparingTo("80.00");
        assertThat(persisted.getPaypalCurrencyCode()).isEqualTo("EUR");
        assertThat(persisted.getPaypalPayeeMerchantId()).isEqualTo("verified-merchant");
        verify(payPalService, times(1)).createOrder(any(Booking.class));
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
        offering.setTitle("PayPal Checkout Service");
        offering.setDescription("Beschreibung");
        offering.setCategory("REPAIR");
        offering.setPrice(80.0);
        offering.setCurrencyCode("EUR");
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
