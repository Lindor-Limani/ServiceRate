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
        booking.setServiceDate(OffsetDateTime.now().plusDays(3));
        booking.setStatus("PENDING");

        Booking saved = bookingRepository.save(booking);

        return new BookingResponse(
                saved.getId(),
                customer.getFirstName() + " " + customer.getLastName(),
                service.getTitle(),
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
                        b.getStatus()
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
                        b.getStatus()
                )).toList();
    }
}