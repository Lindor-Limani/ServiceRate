package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.CreateBookingRequest;
import at.hcw.serviceratebackend.dto.ReviewResponse;
import at.hcw.serviceratebackend.model.common.enums.BookingStatus;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;

    public BookingResponse createBooking(CreateBookingRequest request, String customerEmail) {
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Kunde nicht gefunden"));

        requireAccountType(customer, "CUSTOMER");
        requireVerifiedEmail(customer);

        ServiceOffering service = serviceRepository.findById(request.serviceOfferingId())
                .orElseThrow(() -> new RuntimeException("Service nicht gefunden"));

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(service);
        booking.setServiceDate(OffsetDateTime.now().plusDays(3));
        booking.setBookingDate(request.bookingDate()); // vom Kunden gewählter Wunschtermin
        booking.setStatus(BookingStatus.PENDING.name());

        Booking saved = bookingRepository.save(booking);

        return new BookingResponse(
                saved.getId(),
                customer.getFirstName() + " " + customer.getLastName(),
                service.getTitle(),
                saved.getStatus(),
                saved.getBookingDate(),
                null
        );
    }

    // Holt alle Buchungen für einen bestimmten Handwerker
    public java.util.List<BookingResponse> getBookingsForProvider(UUID providerId) {
        List<Booking> bookings = bookingRepository.findByServiceOffering_Provider_Id(providerId);
        Map<UUID, ReviewResponse> reviews = loadReviewsByBookingId(bookings);

        return bookings.stream()
                .map(b -> toResponse(b, fullName(b.getCustomer()), reviews.get(b.getId())))
                .toList();
    }

    public java.util.List<BookingResponse> getBookingsForProviderEmail(String providerEmail) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider nicht gefunden"));
        requireAccountType(provider, "PROVIDER");
        return getBookingsForProvider(provider.getId());
    }

    // Ändert den Status einer Buchung
    public BookingResponse updateBookingStatus(UUID bookingId, String newStatus, String providerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));

        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider nicht gefunden"));
        requireAccountType(provider, "PROVIDER");
        requireProviderOwnsBooking(provider, booking);

        BookingStatus targetStatus = parseStatus(newStatus);
        if (targetStatus != BookingStatus.ACCEPTED
                && targetStatus != BookingStatus.REJECTED
                && targetStatus != BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Ungültiger Buchungsstatus.");
        }

        booking.setStatus(targetStatus.name());
        Booking saved = bookingRepository.save(booking);

        return new BookingResponse(
                saved.getId(),
                saved.getCustomer().getFirstName() + " " + saved.getCustomer().getLastName(),
                saved.getServiceOffering().getTitle(),
                saved.getStatus(),
                saved.getBookingDate(),
                findReviewResponse(saved)
        );
    }

    // Holt alle Buchungen für das Kunden-Dashboard
    public java.util.List<BookingResponse> getBookingsForCustomer(UUID customerId) {
        List<Booking> bookings = bookingRepository.findByCustomer_Id(customerId);
        Map<UUID, ReviewResponse> reviews = loadReviewsByBookingId(bookings);

        return bookings.stream()
                .map(b -> toResponse(b, providerName(b.getServiceOffering()), reviews.get(b.getId())))
                .toList();
    }

    public java.util.List<BookingResponse> getBookingsForCustomerEmail(String customerEmail) {
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Kunde nicht gefunden"));
        requireAccountType(customer, "CUSTOMER");
        return getBookingsForCustomer(customer.getId());
    }

    private BookingResponse toResponse(Booking booking, String displayName, ReviewResponse review) {
        return new BookingResponse(
                booking.getId(),
                displayName,
                serviceTitle(booking.getServiceOffering()),
                booking.getStatus(),
                booking.getBookingDate(),
                review
        );
    }

    private ReviewResponse findReviewResponse(Booking booking) {
        return reviewRepository.findByBookingId(booking.getId()).stream()
                .findFirst()
                .map(reviewService::toResponse)
                .orElse(null);
    }

    private Map<UUID, ReviewResponse> loadReviewsByBookingId(List<Booking> bookings) {
        List<UUID> bookingIds = bookings.stream()
                .map(Booking::getId)
                .toList();

        if (bookingIds.isEmpty()) {
            return Map.of();
        }

        return reviewRepository.findByBookingIdIn(bookingIds).stream()
                .map(reviewService::toResponse)
                .collect(Collectors.toMap(
                        ReviewResponse::bookingId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    // ── Null-sichere Helfer ────────────────────────────────────────────────────
    // Setzt Vor-/Nachname zusammen; fängt fehlenden User und null-Felder ab
    private String fullName(User user) {
        if (user == null) return "Unbekannter Nutzer";
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last  = user.getLastName()  != null ? user.getLastName()  : "";
        String name  = (first + " " + last).trim();
        return name.isEmpty() ? "Unbekannter Nutzer" : name;
    }

    // Name des Anbieters aus dem ServiceOffering; fängt gelöschten Service/Provider ab
    private String providerName(ServiceOffering offering) {
        if (offering == null) return "Unbekannter Anbieter";
        String name = fullName(offering.getProvider());
        return "Unbekannter Nutzer".equals(name) ? "Unbekannter Anbieter" : name;
    }

    // Titel des Service; fängt gelöschten Service / fehlenden Titel ab
    private String serviceTitle(ServiceOffering offering) {
        if (offering == null || offering.getTitle() == null) return "Unbekannter Service";
        return offering.getTitle();
    }

    private BookingStatus parseStatus(String status) {
        try {
            return BookingStatus.valueOf(status);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ungültiger Buchungsstatus.");
        }
    }

    private void requireAccountType(User user, String expectedType) {
        if (user == null || !expectedType.equals(user.getAccountType())) {
            throw new IllegalArgumentException("Diese Aktion ist für diese Rolle nicht erlaubt.");
        }
    }

    private void requireVerifiedEmail(User user) {
        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException("Bitte verifiziere zuerst deine E-Mail-Adresse.");
        }
    }

    private void requireProviderOwnsBooking(User provider, Booking booking) {
        User owner = booking.getServiceOffering() != null ? booking.getServiceOffering().getProvider() : null;
        if (owner == null || !owner.getId().equals(provider.getId())) {
            throw new IllegalArgumentException("Diese Buchung gehört nicht zu diesem Anbieter.");
        }
    }
}
