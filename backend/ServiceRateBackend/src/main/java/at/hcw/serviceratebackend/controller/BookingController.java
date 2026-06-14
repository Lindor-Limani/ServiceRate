package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.CreateBookingRequest;
import at.hcw.serviceratebackend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@RequestBody CreateBookingRequest request) {
        return ResponseEntity.ok(bookingService.createBooking(request));
    }

    // Holt die Buchungen für das Handwerker-Dashboard
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<java.util.List<BookingResponse>> getBookingsForProvider(@PathVariable java.util.UUID providerId) {
        return ResponseEntity.ok(bookingService.getBookingsForProvider(providerId));
    }

    // Ändert den Status einer Buchung (PUT)
    @PutMapping("/{id}/status")
    public ResponseEntity<BookingResponse> updateStatus(
            @PathVariable java.util.UUID id,
            @RequestBody at.hcw.serviceratebackend.dto.UpdateBookingStatusRequest request) {
        return ResponseEntity.ok(bookingService.updateBookingStatus(id, request.status()));
    }

    // Ändert das Termin-Datum; setzt Status automatisch auf PENDING
    @PutMapping("/{id}/date")
    public ResponseEntity<BookingResponse> updateDate(
            @PathVariable java.util.UUID id,
            @RequestBody at.hcw.serviceratebackend.dto.UpdateBookingDateRequest request) {
        return ResponseEntity.ok(bookingService.updateBookingDate(id, request.serviceDate()));
    }

    // Endpunkt für die Kunden-App
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<java.util.List<BookingResponse>> getBookingsForCustomer(@PathVariable java.util.UUID customerId) {
        return ResponseEntity.ok(bookingService.getBookingsForCustomer(customerId));
    }
}