package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.common.enums.BookingStatus;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.Review;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ServiceOfferingRepositoryTest {

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    void searchActive_filtersByTextCategoryLocationPriceAndMinimumRating() {
        User provider = saveUser("provider@example.com", "PROVIDER", "Ada", "Builder");
        User customer = saveUser("customer@example.com", "CUSTOMER", "Grace", "Customer");
        ServiceOffering matching = saveOffering(provider, "Rohr reparieren", "Bad und Kueche", "REPAIR", "Wien", new BigDecimal("80.00"), "ACTIVE");
        ServiceOffering tooExpensive = saveOffering(provider, "Rohr reparieren teuer", "Bad", "REPAIR", "Wien", new BigDecimal("180.00"), "ACTIVE");
        ServiceOffering inactive = saveOffering(provider, "Rohr reparieren inaktiv", "Bad", "REPAIR", "Wien", new BigDecimal("50.00"), "INACTIVE");
        ServiceOffering otherCategory = saveOffering(provider, "Rohr reinigen", "Bad", "CLEANING", "Wien", new BigDecimal("70.00"), "ACTIVE");
        addReview(customer, matching, 5);
        addReview(customer, tooExpensive, 5);
        addReview(customer, inactive, 5);
        addReview(customer, otherCategory, 5);

        var page = serviceOfferingRepository.searchActive(
                "rohr",
                "REPAIR",
                "wien",
                new BigDecimal("100.00"),
                4.5,
                PageRequest.of(0, 10, Sort.by("title"))
        );

        assertThat(page.getContent())
                .extracting(ServiceOffering::getId)
                .containsExactly(matching.getId());
        assertThat(serviceOfferingRepository.findById(matching.getId()).orElseThrow().getPrice())
                .isEqualByComparingTo("80.00");
    }

    @Test
    void searchActive_treatsMissingReviewsAsZeroRatingForMinRatingFilter() {
        User provider = saveUser("provider@example.com", "PROVIDER", "Ada", "Builder");
        ServiceOffering unrated = saveOffering(provider, "Fenster montieren", "Montage", "REPAIR", "Graz", new BigDecimal("90.00"), "ACTIVE");

        var visibleWithoutMinimum = serviceOfferingRepository.searchActive(
                "",
                "",
                "",
                null,
                0.0,
                PageRequest.of(0, 10)
        );
        var hiddenWithMinimum = serviceOfferingRepository.searchActive(
                "",
                "",
                "",
                null,
                1.0,
                PageRequest.of(0, 10)
        );

        assertThat(visibleWithoutMinimum.getContent())
                .extracting(ServiceOffering::getId)
                .contains(unrated.getId());
        assertThat(hiddenWithMinimum.getContent())
                .extracting(ServiceOffering::getId)
                .doesNotContain(unrated.getId());
    }

    @Test
    void reviewRepository_returnsAverageCountAndReviewsForService() {
        User provider = saveUser("provider@example.com", "PROVIDER", "Ada", "Builder");
        User customer = saveUser("customer@example.com", "CUSTOMER", "Grace", "Customer");
        ServiceOffering service = saveOffering(provider, "Bad sanieren", "Komplett", "REPAIR", "Linz", new BigDecimal("120.00"), "ACTIVE");
        addReview(customer, service, 4);
        addReview(customer, service, 5);

        assertThat(reviewRepository.findAverageRatingByServiceId(service.getId())).isEqualTo(4.5);
        assertThat(reviewRepository.findReviewCountByServiceId(service.getId())).isEqualTo(2L);
        assertThat(reviewRepository.findByBookingServiceOfferingId(service.getId())).hasSize(2);
    }

    private User saveUser(String email, String accountType, String firstName, String lastName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setAccountType(accountType);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private ServiceOffering saveOffering(
            User provider,
            String title,
            String description,
            String category,
            String location,
            BigDecimal price,
            String status
    ) {
        ServiceOffering service = new ServiceOffering();
        service.setId(UUID.randomUUID());
        service.setProvider(provider);
        service.setTitle(title);
        service.setDescription(description);
        service.setCategory(category);
        service.setLocation(location);
        service.setPrice(price);
        service.setStatus(status);
        service.setDeliverableType("ON_SITE");
        return serviceOfferingRepository.saveAndFlush(service);
    }

    private void addReview(User customer, ServiceOffering service, int rating) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(service);
        booking.setServiceDate(OffsetDateTime.now().plusDays(1));
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStatus(BookingStatus.COMPLETED.name());
        booking.setPaymentStatus("PAID");
        booking = bookingRepository.saveAndFlush(booking);

        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setBooking(booking);
        review.setReviewer(customer);
        review.setRating(rating);
        review.setComment("ok");
        reviewRepository.saveAndFlush(review);
    }
}
