package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.CreateReviewRequest;
import at.hcw.serviceratebackend.dto.ReviewResponse;
import at.hcw.serviceratebackend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
}
