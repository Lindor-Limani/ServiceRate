package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.*;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.CreateBookingRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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
        // Use provided serviceDate if present; fallback to now()+3 days for backward compatibility
        OffsetDateTime desiredDate = request.serviceDate() != null ? request.serviceDate() : OffsetDateTime.now().plusDays(3);
        booking.setServiceDate(desiredDate);
        booking.setStatus("PENDING");

        Booking saved = bookingRepository.save(booking);

        return new BookingResponse(
                saved.getId(),
                customer.getFirstName() + " " + customer.getLastName(),
                service.getTitle(),
                desiredDate,
                saved.getStatus()
        );
    }

    // Holt alle Buchungen für einen bestimmten Handwerker
    public java.util.List<BookingResponse> getBookingsForProvider(UUID providerId) {
        return bookingRepository.findByServiceOffering_Provider_Id(providerId).stream()
                .map(b -> new BookingResponse(
                        b.getId(),
                        b.getCustomer().getFirstName() + " " + b.getCustomer().getLastName(),
                        b.getServiceOffering().getTitle(),
                        b.getServiceDate(),
                        b.getStatus()
                ))
                .toList();
    }

    // Ändert den Status einer Buchung (ohne Identitätsprüfung)
    public BookingResponse updateBookingStatus(UUID bookingId, String newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));

        booking.setStatus(newStatus);
        Booking saved = bookingRepository.save(booking);

        return new BookingResponse(
                saved.getId(),
                saved.getCustomer().getFirstName() + " " + saved.getCustomer().getLastName(),
                saved.getServiceOffering().getTitle(),
                saved.getServiceDate(),
                saved.getStatus()
        );
    }

    public BookingResponse updateBookingDate(UUID bookingId, OffsetDateTime newDate) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));

        if (!newDate.isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Das neue Datum muss in der Zukunft liegen");
        }
        booking.setServiceDate(newDate);
        Booking saved = bookingRepository.save(booking);

        return new BookingResponse(
                saved.getId(),
                saved.getCustomer().getFirstName() + " " + saved.getCustomer().getLastName(),
                saved.getServiceOffering().getTitle(),
                saved.getServiceDate(),
                saved.getStatus()
        );
    }

    // Ändert den Status einer Buchung – Identifikation via customerId
    public BookingResponse updateBookingStatusForCustomer(UUID bookingId, UUID customerId, String newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        if (booking.getCustomer() == null || booking.getCustomer().getId() == null || !booking.getCustomer().getId().equals(customerId)) {
            throw new IllegalArgumentException("Ungültiger Kunde für diese Buchung");
        }
        booking.setStatus(newStatus);
        Booking saved = bookingRepository.save(booking);
        return new BookingResponse(
                saved.getId(),
                saved.getCustomer().getFirstName() + " " + saved.getCustomer().getLastName(),
                saved.getServiceOffering().getTitle(),
                saved.getServiceDate(),
                saved.getStatus()
        );
    }

    // Ändert den Status einer Buchung – Identifikation via providerId (Anbieter)
    public BookingResponse updateBookingStatusForProvider(UUID bookingId, UUID providerId, String newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User provider = booking.getServiceOffering() != null ? booking.getServiceOffering().getProvider() : null;
        if (provider == null || provider.getId() == null || !provider.getId().equals(providerId)) {
            throw new IllegalArgumentException("Ungültiger Anbieter für diese Buchung");
        }
        booking.setStatus(newStatus);
        Booking saved = bookingRepository.save(booking);
        return new BookingResponse(
                saved.getId(),
                saved.getCustomer().getFirstName() + " " + saved.getCustomer().getLastName(),
                saved.getServiceOffering().getTitle(),
                saved.getServiceDate(),
                saved.getStatus()
        );
    }

    // Holt alle Buchungen für das Kunden-Dashboard
    public java.util.List<BookingResponse> getBookingsForCustomer(UUID customerId) {
        return bookingRepository.findByCustomer_Id(customerId).stream()
                .map(b -> new BookingResponse(
                        b.getId(),
                        // HIER GEÄNDERT: Wir holen den Namen des Anbieters (Providers)!
                        b.getServiceOffering().getProvider().getFirstName() + " " + b.getServiceOffering().getProvider().getLastName(),
                        b.getServiceOffering().getTitle(),
                        b.getServiceDate(),
                        b.getStatus()
                )).toList();
    }
}