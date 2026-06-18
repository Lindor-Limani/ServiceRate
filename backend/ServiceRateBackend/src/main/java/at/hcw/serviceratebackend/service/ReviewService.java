package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateReviewRequest;
import at.hcw.serviceratebackend.dto.ReviewResponse;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.Review;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;

    public ReviewResponse create(CreateReviewRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Buchung nicht gefunden"));

        // Bewerten ist nur erlaubt, wenn die Buchung akzeptiert wurde -> sonst 400 Bad Request
        if (!"ACCEPTED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Eine Bewertung ist nur für akzeptierte Buchungen möglich.");
        }

        // Der Bewerter ist der Kunde der Buchung
        User reviewer = booking.getCustomer();

        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setBooking(booking);
        review.setReviewer(reviewer);
        review.setRating(request.rating());
        review.setComment(request.comment());

        Review saved = reviewRepository.save(review);

        return new ReviewResponse(
                saved.getId(),
                booking.getId(),
                reviewer.getFirstName() + " " + reviewer.getLastName(),
                booking.getServiceOffering().getTitle(),
                saved.getRating(),
                saved.getComment()
        );
    }


    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsForBookingId(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Buchung nicht gefunden!"));

        return reviewRepository.findByBookingId(bookingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsForServiceId(UUID serviceId) {
        return reviewRepository.findByBookingServiceOfferingId(serviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ReviewResponse toResponse(Review review) {
        Booking booking = review.getBooking();
        User reviewer = review.getReviewer();

        return new ReviewResponse(
                review.getId(),
                booking.getId(),
                reviewer.getFirstName() + " " + reviewer.getLastName(),
                booking.getServiceOffering().getTitle(),
                review.getRating(),
                review.getComment()
        );
    }
}
