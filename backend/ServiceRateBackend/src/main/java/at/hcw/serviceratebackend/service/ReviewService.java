package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateReviewRequest;
import at.hcw.serviceratebackend.dto.ReviewResponse;
import at.hcw.serviceratebackend.model.common.exception.ConflictException;
import at.hcw.serviceratebackend.model.common.enums.BookingStatus;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.Review;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final MailService mailService;

    @Transactional
    public ReviewResponse create(CreateReviewRequest request, String customerEmail) {
        Booking booking = bookingRepository.findByIdForReviewCreation(request.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Buchung nicht gefunden"));

        User reviewer = booking.getCustomer();
        if (reviewer == null || customerEmail == null || !customerEmail.equals(reviewer.getEmail())) {
            throw new AccessDeniedException("Diese Buchung gehört nicht zu diesem Kunden.");
        }

        // Bewerten ist erst erlaubt, wenn die Leistung abgeschlossen wurde.
        if (!BookingStatus.COMPLETED.name().equals(booking.getStatus())) {
            throw new IllegalArgumentException("Eine Bewertung ist erst nach abgeschlossener Buchung möglich.");
        }

        if (reviewRepository.existsByBookingId(booking.getId())) {
            throw reviewAlreadyExists();
        }

        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setBooking(booking);
        review.setReviewer(reviewer);
        review.setRating(request.rating());
        review.setComment(request.comment());

        Review saved;
        try {
            saved = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            throw reviewAlreadyExists();
        }
        mailService.sendReviewCreatedMail(saved);

        return new ReviewResponse(
                saved.getId(),
                booking.getId(),
                reviewer.getFirstName() + " " + reviewer.getLastName(),
                booking.getServiceOffering().getTitle(),
                saved.getRating(),
                saved.getComment()
        );
    }

    private ConflictException reviewAlreadyExists() {
        return new ConflictException("Für diese Buchung wurde bereits eine Bewertung erstellt.");
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
