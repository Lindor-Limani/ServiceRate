package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.CreateBookingRequest;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceRepository;

    public BookingResponse createBooking(CreateBookingRequest request) {
        User customer = userRepository.findById(request.customerId())
                .orElseThrow(() -> new RuntimeException("Kunde nicht gefunden"));

        ServiceOffering service = serviceRepository.findById(request.serviceOfferingId())
                .orElseThrow(() -> new RuntimeException("Service nicht gefunden"));

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(service);
        booking.setServiceDate(OffsetDateTime.now().plusDays(3));
        booking.setBookingDate(request.bookingDate()); // vom Kunden gewählter Wunschtermin
        booking.setStatus("PENDING");

        Booking saved = bookingRepository.save(booking);

        return new BookingResponse(
                saved.getId(),
                customer.getFirstName() + " " + customer.getLastName(),
                service.getTitle(),
                saved.getStatus(),
                saved.getBookingDate()
        );
    }

    // Holt alle Buchungen für einen bestimmten Handwerker
    public java.util.List<BookingResponse> getBookingsForProvider(UUID providerId) {
        return bookingRepository.findByServiceOffering_Provider_Id(providerId).stream()
                .map(b -> new BookingResponse(
                        b.getId(),
                        // Name des Kunden – NPE-sicher, falls Kunde/Felder bei Altdaten fehlen
                        fullName(b.getCustomer()),
                        serviceTitle(b.getServiceOffering()),
                        b.getStatus(),
                        b.getBookingDate()
                ))
                .toList();
    }

    // Ändert den Status einer Buchung
    public BookingResponse updateBookingStatus(UUID bookingId, String newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));

        booking.setStatus(newStatus);
        Booking saved = bookingRepository.save(booking);

        return new BookingResponse(
                saved.getId(),
                saved.getCustomer().getFirstName() + " " + saved.getCustomer().getLastName(),
                saved.getServiceOffering().getTitle(),
                saved.getStatus(),
                saved.getBookingDate()
        );
    }

    // Holt alle Buchungen für das Kunden-Dashboard
    public java.util.List<BookingResponse> getBookingsForCustomer(UUID customerId) {
        return bookingRepository.findByCustomer_Id(customerId).stream()
                .map(b -> new BookingResponse(
                        b.getId(),
                        // Im Kunden-Dashboard zeigen wir den Namen des Anbieters – NPE-sicher
                        providerName(b.getServiceOffering()),
                        serviceTitle(b.getServiceOffering()),
                        b.getStatus(),
                        b.getBookingDate()
                )).toList();
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
}