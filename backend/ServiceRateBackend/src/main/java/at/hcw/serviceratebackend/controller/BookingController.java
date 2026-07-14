package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.CreateCheckoutRequest;
import at.hcw.serviceratebackend.dto.CreateBookingRequest;
import at.hcw.serviceratebackend.dto.CreateTimeEntryRequest;
import at.hcw.serviceratebackend.dto.PayPalCaptureRequest;
import at.hcw.serviceratebackend.dto.PublishDeliveryRequest;
import at.hcw.serviceratebackend.dto.RecordPaymentRequest;
import at.hcw.serviceratebackend.dto.UpdateBookingWorkRequest;
import at.hcw.serviceratebackend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

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

    // Ändert den Status einer Buchung (PUT)
    @PutMapping("/{id}/status")
    public ResponseEntity<BookingResponse> updateStatus(
            @PathVariable("id") java.util.UUID id,
            @RequestBody at.hcw.serviceratebackend.dto.UpdateBookingStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.updateBookingStatus(id, request.status(), (String) authentication.getPrincipal()));
    }

    @PutMapping("/{id}/work")
    public ResponseEntity<BookingResponse> updateWork(
            @PathVariable("id") java.util.UUID id,
            @RequestBody UpdateBookingWorkRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.updateWorkLog(id, request, (String) authentication.getPrincipal()));
    }

    @PostMapping("/{id}/time-entries")
    public ResponseEntity<BookingResponse> addTimeEntry(
            @PathVariable("id") java.util.UUID id,
            @RequestBody CreateTimeEntryRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.addTimeEntry(id, request, (String) authentication.getPrincipal()));
    }

    @PostMapping("/{id}/delivery")
    public ResponseEntity<BookingResponse> publishDelivery(
            @PathVariable("id") java.util.UUID id,
            @RequestBody PublishDeliveryRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.publishDelivery(id, request, (String) authentication.getPrincipal()));
    }

    @GetMapping("/{id}/delivery/open")
    public ResponseEntity<Void> openDelivery(
            @PathVariable("id") java.util.UUID id,
            Authentication authentication) {
        String target = bookingService.resolveDeliveryUrl(id, (String) authentication.getPrincipal());
        return ResponseEntity.status(302).location(URI.create(target)).build();
    }

    @GetMapping("/{id}/delivery/url")
    public Map<String, String> getDeliveryUrl(
            @PathVariable("id") java.util.UUID id,
            Authentication authentication) {
        String target = bookingService.resolveDeliveryUrl(id, (String) authentication.getPrincipal());
        return Map.of("url", target);
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<BookingResponse> createCheckout(
            @PathVariable("id") java.util.UUID id,
            @RequestBody CreateCheckoutRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.createCheckout(id, request, (String) authentication.getPrincipal()));
    }

    @PostMapping("/{id}/paypal/capture")
    public ResponseEntity<BookingResponse> capturePayPalPayment(
            @PathVariable("id") java.util.UUID id,
            @RequestBody PayPalCaptureRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.capturePayPalPayment(id, request.orderId(), (String) authentication.getPrincipal()));
    }

    @PostMapping("/{id}/record-payment")
    public ResponseEntity<BookingResponse> recordPayment(
            @PathVariable("id") java.util.UUID id,
            @RequestBody RecordPaymentRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.recordProviderPayment(id, request, (String) authentication.getPrincipal()));
    }

    // Endpunkt für die Kunden-App
    @GetMapping("/customer/me")
    public ResponseEntity<java.util.List<BookingResponse>> getMyCustomerBookings(Authentication authentication) {
        return ResponseEntity.ok(bookingService.getBookingsForCustomerEmail((String) authentication.getPrincipal()));
    }

}
