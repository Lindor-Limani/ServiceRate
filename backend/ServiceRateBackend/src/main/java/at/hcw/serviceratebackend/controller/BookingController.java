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

    // Spezifische Status-Endpunkte (vom Anbieter auszuführen)
    @PostMapping("/{id}/status/accept")
    public ResponseEntity<BookingResponse> accept(@PathVariable java.util.UUID id,
                                                  @RequestParam("providerId") java.util.UUID providerId) {
        return ResponseEntity.ok(bookingService.updateBookingStatusForProvider(id, providerId, "ACCEPTED"));
    }

    @PostMapping("/{id}/status/reject")
    public ResponseEntity<BookingResponse> reject(@PathVariable java.util.UUID id,
                                                  @RequestParam("providerId") java.util.UUID providerId) {
        return ResponseEntity.ok(bookingService.updateBookingStatusForProvider(id, providerId, "REJECTED"));
    }

    @PostMapping("/{id}/status/complete")
    public ResponseEntity<BookingResponse> complete(@PathVariable java.util.UUID id,
                                                    @RequestParam("providerId") java.util.UUID providerId) {
        return ResponseEntity.ok(bookingService.updateBookingStatusForProvider(id, providerId, "COMPLETED"));
    }

    // Endpunkt für die Kunden-App
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<java.util.List<BookingResponse>> getBookingsForCustomer(@PathVariable java.util.UUID customerId) {
        return ResponseEntity.ok(bookingService.getBookingsForCustomer(customerId));
    }
}