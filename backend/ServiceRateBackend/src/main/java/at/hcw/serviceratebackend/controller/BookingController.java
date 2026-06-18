package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.CreateBookingRequest;
import at.hcw.serviceratebackend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@RequestBody CreateBookingRequest request, Authentication authentication) {
        return ResponseEntity.ok(bookingService.createBooking(request, (String) authentication.getPrincipal()));
    }

    @GetMapping("/provider/me")
    public ResponseEntity<java.util.List<BookingResponse>> getMyProviderBookings(Authentication authentication) {
        return ResponseEntity.ok(bookingService.getBookingsForProviderEmail((String) authentication.getPrincipal()));
    }

    // Holt die Buchungen für das Handwerker-Dashboard
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<java.util.List<BookingResponse>> getBookingsForProvider(@PathVariable("providerId") java.util.UUID providerId) {
        return ResponseEntity.ok(bookingService.getBookingsForProvider(providerId));
    }

    // Ändert den Status einer Buchung (PUT)
    @PutMapping("/{id}/status")
    public ResponseEntity<BookingResponse> updateStatus(
            @PathVariable("id") java.util.UUID id,
            @RequestBody at.hcw.serviceratebackend.dto.UpdateBookingStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.updateBookingStatus(id, request.status(), (String) authentication.getPrincipal()));
    }

    // Endpunkt für die Kunden-App
    @GetMapping("/customer/me")
    public ResponseEntity<java.util.List<BookingResponse>> getMyCustomerBookings(Authentication authentication) {
        return ResponseEntity.ok(bookingService.getBookingsForCustomerEmail((String) authentication.getPrincipal()));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<java.util.List<BookingResponse>> getBookingsForCustomer(@PathVariable("customerId") java.util.UUID customerId) {
        return ResponseEntity.ok(bookingService.getBookingsForCustomer(customerId));
    }
}
