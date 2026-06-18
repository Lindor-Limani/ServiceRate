package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.CreateReviewRequest;
import at.hcw.serviceratebackend.dto.ReviewResponse;
import at.hcw.serviceratebackend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@Valid @RequestBody CreateReviewRequest request) {
        return reviewService.create(request);
    }

    @GetMapping("/booking/{bookingId}")
    public List<ReviewResponse> getReviewsForBooking(@PathVariable("bookingId") UUID bookingId) {
        return reviewService.getReviewsForBookingId(bookingId);
    }

    @GetMapping("/service/{serviceId}")
    public List<ReviewResponse> getReviewsForService(@PathVariable("serviceId") UUID serviceId) {
        return reviewService.getReviewsForServiceId(serviceId);
    }
}
