package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.CreateCheckoutRequest;
import at.hcw.serviceratebackend.model.common.exception.ConflictException;
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
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class BookingStatusConcurrencyIntegrationTest {

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
    private MailService mailService;

    @MockitoBean
    private PayPalService payPalService;

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
    void competingAcceptAndRejectRequestsProduceExactlyOneTransitionAndNotification() throws Exception {
        User customer = saveUser("status-customer@example.com", "CUSTOMER");
        User provider = saveUser("status-provider@example.com", "PROVIDER");
        Booking booking = savePendingBooking(customer, saveService(provider));

        CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            String target = i % 2 == 0 ? "ACCEPTED" : "REJECTED";
            results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Paralleler Teststart fehlgeschlagen");
                }
                try {
                    bookingService.updateBookingStatus(booking.getId(), target, provider.getEmail());
                    return true;
                } catch (ConflictException ex) {
                    assertThat(ex).hasMessage(
                            "Statuswechsel ist für den aktuellen Buchungsstatus nicht erlaubt."
                    );
                    return false;
                }
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successfulTransitions = 0;
        for (Future<Boolean> result : results) {
            if (result.get(20, TimeUnit.SECONDS)) {
                successfulTransitions++;
            }
        }

        assertThat(successfulTransitions).isEqualTo(1);
        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isIn("ACCEPTED", "REJECTED");
        verify(mailService, times(1)).sendBookingStatusMail(argThat(changed ->
                booking.getId().equals(changed.getId())
                        && ("ACCEPTED".equals(changed.getStatus()) || "REJECTED".equals(changed.getStatus()))
        ));
    }

    @Test
    void checkoutAndCompletionAreSerializedWithoutLosingEitherChange() throws Exception {
        User customer = saveUser("checkout-race-customer@example.com", "CUSTOMER");
        User provider = saveUser("checkout-race-provider@example.com", "PROVIDER");
        provider.setPaypalMerchantId("verified-merchant");
        userRepository.saveAndFlush(provider);
        Booking booking = savePendingBooking(customer, saveService(provider));
        booking.setStatus("ACCEPTED");
        booking = bookingRepository.saveAndFlush(booking);

        CountDownLatch checkoutReachedProvider = new CountDownLatch(1);
        CountDownLatch releaseCheckout = new CountDownLatch(1);
        CountDownLatch completionAttempted = new CountDownLatch(1);
        when(payPalService.isProviderCheckoutEligible(any(User.class))).thenReturn(true);
        when(payPalService.createOrder(any(Booking.class))).thenAnswer(invocation -> {
            checkoutReachedProvider.countDown();
            if (!releaseCheckout.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Checkout-Testfreigabe fehlgeschlagen");
            }
            return new PayPalService.PayPalOrder("RACE-ORDER", "https://paypal.example/race");
        });

        UUID bookingId = booking.getId();
        Future<BookingResponse> checkout = executor.submit(() -> bookingService.createCheckout(
                bookingId,
                new CreateCheckoutRequest("PAYPAL", false),
                customer.getEmail()
        ));
        assertThat(checkoutReachedProvider.await(10, TimeUnit.SECONDS)).isTrue();

        Future<BookingResponse> completion = executor.submit(() -> {
            completionAttempted.countDown();
            return bookingService.updateBookingStatus(bookingId, "COMPLETED", provider.getEmail());
        });
        assertThat(completionAttempted.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(() -> completion.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseCheckout.countDown();
        }

        BookingResponse checkoutResponse = checkout.get(10, TimeUnit.SECONDS);
        BookingResponse completionResponse = completion.get(10, TimeUnit.SECONDS);
        Booking persisted = bookingRepository.findById(bookingId).orElseThrow();

        assertThat(checkoutResponse.status()).isEqualTo("ACCEPTED");
        assertThat(completionResponse.status()).isEqualTo("COMPLETED");
        assertThat(persisted.getStatus()).isEqualTo("COMPLETED");
        assertThat(persisted.getPaymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(persisted.getPaymentProvider()).isEqualTo("PAYPAL");
        assertThat(persisted.getPaypalOrderId()).isEqualTo("RACE-ORDER");
        assertThat(persisted.getCheckoutUrl()).isEqualTo("https://paypal.example/race");
        verify(payPalService, times(1)).createOrder(any(Booking.class));
        verify(mailService, times(1)).sendBookingStatusMail(argThat(changed ->
                bookingId.equals(changed.getId()) && "COMPLETED".equals(changed.getStatus())
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
        offering.setTitle("Status Service");
        offering.setDescription("Beschreibung");
        offering.setCategory("REPAIR");
        offering.setPrice(80.0);
        offering.setStatus("ACTIVE");
        return serviceOfferingRepository.saveAndFlush(offering);
    }

    private Booking savePendingBooking(User customer, ServiceOffering offering) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(offering);
        booking.setServiceDate(OffsetDateTime.now().plusDays(1));
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStatus("PENDING");
        booking.setPaymentStatus("UNPAID");
        return bookingRepository.saveAndFlush(booking);
    }
}
