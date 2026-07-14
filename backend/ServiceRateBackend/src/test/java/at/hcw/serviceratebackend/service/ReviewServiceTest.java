package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateReviewRequest;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.Review;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private MailService mailService;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, bookingRepository, mailService);
    }

    @Test
    void create_allowsAuthenticatedBookingCustomerForCompletedBooking() {
        Booking booking = completedBooking("owner@example.com");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = reviewService.create(
                new CreateReviewRequest(booking.getId(), 5, "Sehr gut"),
                "owner@example.com"
        );

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        Review saved = reviewCaptor.getValue();
        assertThat(saved.getBooking()).isSameAs(booking);
        assertThat(saved.getReviewer()).isSameAs(booking.getCustomer());
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getComment()).isEqualTo("Sehr gut");
        assertThat(response.bookingId()).isEqualTo(booking.getId());
        assertThat(response.reviewerName()).isEqualTo("Booking Customer");
        verify(mailService).sendReviewCreatedMail(saved);
    }

    @Test
    void create_rejectsForeignCustomerWithoutSavingOrSendingMail() {
        Booking booking = completedBooking("owner@example.com");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> reviewService.create(
                new CreateReviewRequest(booking.getId(), 5, "Manipuliert"),
                "OWNER@example.com"
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Diese Buchung gehört nicht zu diesem Kunden.");

        verify(reviewRepository, never()).save(any());
        verify(mailService, never()).sendReviewCreatedMail(any());
    }

    @Test
    void create_rejectsMissingBookingWithoutSavingOrSendingMail() {
        UUID missingId = UUID.randomUUID();
        when(bookingRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(
                new CreateReviewRequest(missingId, 4, "Nicht vorhanden"),
                "owner@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Buchung nicht gefunden");

        verify(reviewRepository, never()).save(any());
        verify(mailService, never()).sendReviewCreatedMail(any());
    }

    @Test
    void create_rejectsOwnBookingBeforeCompletionWithoutSavingOrSendingMail() {
        Booking booking = completedBooking("owner@example.com");
        booking.setStatus("ACCEPTED");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> reviewService.create(
                new CreateReviewRequest(booking.getId(), 4, "Zu früh"),
                "owner@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eine Bewertung ist erst nach abgeschlossener Buchung möglich.");

        verify(reviewRepository, never()).save(any());
        verify(mailService, never()).sendReviewCreatedMail(any());
    }

    private Booking completedBooking(String customerEmail) {
        User customer = new User();
        customer.setId(UUID.randomUUID());
        customer.setEmail(customerEmail);
        customer.setFirstName("Booking");
        customer.setLastName("Customer");

        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setTitle("Service");

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(offering);
        booking.setStatus("COMPLETED");
        return booking;
    }
}
