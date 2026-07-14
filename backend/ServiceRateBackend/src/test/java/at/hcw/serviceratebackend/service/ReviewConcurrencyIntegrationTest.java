package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateReviewRequest;
import at.hcw.serviceratebackend.model.common.exception.ConflictException;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.Review;
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
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ReviewConcurrencyIntegrationTest {

    private static final int PARALLEL_REQUESTS = 10;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @MockitoBean
    private MailService mailService;

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
    void parallelCreatesPersistAndNotifyExactlyOnce() throws Exception {
        User customer = saveUser("customer@example.com", "CUSTOMER");
        User provider = saveUser("provider@example.com", "PROVIDER");
        ServiceOffering offering = saveService(provider);
        Booking booking = saveCompletedBooking(customer, offering);
        CreateReviewRequest request = new CreateReviewRequest(booking.getId(), 5, "Parallel");

        CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Paralleler Teststart fehlgeschlagen");
                }
                try {
                    reviewService.create(request, customer.getEmail());
                    return true;
                } catch (ConflictException ex) {
                    assertThat(ex).hasMessage("Für diese Buchung wurde bereits eine Bewertung erstellt.");
                    return false;
                }
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successfulCreates = 0;
        for (Future<Boolean> result : results) {
            if (result.get(20, TimeUnit.SECONDS)) {
                successfulCreates++;
            }
        }

        assertThat(successfulCreates).isEqualTo(1);
        assertThat(reviewRepository.findByBookingId(booking.getId())).hasSize(1);
        verify(mailService, times(1)).sendReviewCreatedMail(
                org.mockito.ArgumentMatchers.argThat(review ->
                        booking.getId().equals(review.getBooking().getId()))
        );
    }

    @Test
    void databaseConstraintRejectsSecondReviewForSameBooking() {
        User customer = saveUser("customer@example.com", "CUSTOMER");
        User provider = saveUser("provider@example.com", "PROVIDER");
        Booking booking = saveCompletedBooking(customer, saveService(provider));

        reviewRepository.saveAndFlush(review(booking, customer, "Erste Bewertung"));

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(review(booking, customer, "Duplikat")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(reviewRepository.findByBookingId(booking.getId())).hasSize(1);
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
        offering.setTitle("Review Service");
        offering.setDescription("Beschreibung");
        offering.setCategory("REPAIR");
        offering.setPrice(80.0);
        offering.setStatus("ACTIVE");
        return serviceOfferingRepository.saveAndFlush(offering);
    }

    private Booking saveCompletedBooking(User customer, ServiceOffering offering) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(offering);
        booking.setServiceDate(OffsetDateTime.now().plusDays(1));
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStatus("COMPLETED");
        booking.setPaymentStatus("PAID");
        return bookingRepository.saveAndFlush(booking);
    }

    private Review review(Booking booking, User reviewer, String comment) {
        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setBooking(booking);
        review.setReviewer(reviewer);
        review.setRating(5);
        review.setComment(comment);
        return review;
    }
}
