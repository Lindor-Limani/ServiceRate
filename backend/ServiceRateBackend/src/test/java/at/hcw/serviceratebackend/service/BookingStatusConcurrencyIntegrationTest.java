package at.hcw.serviceratebackend.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
