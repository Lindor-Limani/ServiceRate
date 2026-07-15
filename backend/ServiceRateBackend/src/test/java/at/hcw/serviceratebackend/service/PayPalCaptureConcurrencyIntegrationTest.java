package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PayPalCaptureConcurrencyIntegrationTest {

    private static final int PARALLEL_REQUESTS = 10;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private PayPalService payPalService;

    @MockitoBean
    private MailService mailService;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
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
    void parallelCaptureReplaysProduceExactlyOneProviderAndPersistenceEffect() throws Exception {
        User customer = saveUser("capture-customer@example.com", "CUSTOMER");
        User provider = saveUser("capture-provider@example.com", "PROVIDER");
        Booking booking = saveCheckoutBooking(customer, saveService(provider));

        CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch captureAttemptsStarted = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch firstCaptureEntered = new CountDownLatch(1);
        CountDownLatch releaseCapture = new CountDownLatch(1);
        AtomicInteger captureInvocations = new AtomicInteger();
        when(payPalService.captureOrder(booking.getId(), "ORDER-RACE")).thenAnswer(invocation -> {
            captureInvocations.incrementAndGet();
            firstCaptureEntered.countDown();
            if (!releaseCapture.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Capture-Testfreigabe fehlgeschlagen");
            }
            return new PayPalService.PayPalCapture("COMPLETED", "CAPTURE-RACE");
        });

        List<Future<BookingResponse>> results = new ArrayList<>();
        for (int request = 0; request < PARALLEL_REQUESTS; request++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Paralleler Teststart fehlgeschlagen");
                }
                captureAttemptsStarted.countDown();
                return bookingService.capturePayPalPayment(
                        booking.getId(), "ORDER-RACE", customer.getEmail()
                );
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(captureAttemptsStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(firstCaptureEntered.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            Thread.sleep(300);
            assertThat(captureInvocations).hasValue(1);
        } finally {
            releaseCapture.countDown();
        }

        for (Future<BookingResponse> result : results) {
            BookingResponse response = result.get(20, TimeUnit.SECONDS);
            assertThat(response.paymentStatus()).isEqualTo("PAID");
            assertThat(response.paypalCaptureId()).isEqualTo("CAPTURE-RACE");
        }

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getPaymentStatus()).isEqualTo("PAID");
        assertThat(persisted.getPaypalCaptureId()).isEqualTo("CAPTURE-RACE");
        assertThat(persisted.getPaidAt()).isNotNull();
        verify(payPalService, times(1)).captureOrder(booking.getId(), "ORDER-RACE");
        verify(mailService, times(1)).sendPaymentRecordedMail(argThat(changed ->
                booking.getId().equals(changed.getId())
                        && "PAID".equals(changed.getPaymentStatus())
                        && "CAPTURE-RACE".equals(changed.getPaypalCaptureId())
        ));
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
        offering.setTitle("Capture Service");
        offering.setDescription("Beschreibung");
        offering.setCategory("REPAIR");
        offering.setPrice(80.0);
        offering.setStatus("ACTIVE");
        return serviceOfferingRepository.saveAndFlush(offering);
    }

    private Booking saveCheckoutBooking(User customer, ServiceOffering offering) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(offering);
        booking.setServiceDate(OffsetDateTime.now().plusDays(1));
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStatus("ACCEPTED");
        booking.setPaymentProvider("PAYPAL");
        booking.setPaymentStatus("CHECKOUT_CREATED");
        booking.setPaypalOrderId("ORDER-RACE");
        booking.setSettlementStatus("PAYPAL_PLATFORM_FEE_PENDING");
        return bookingRepository.saveAndFlush(booking);
    }
}
