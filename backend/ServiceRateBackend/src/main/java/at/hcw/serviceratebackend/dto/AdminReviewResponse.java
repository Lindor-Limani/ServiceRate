package at.hcw.serviceratebackend.dto;

import java.util.UUID;

public record AdminReviewResponse(
        UUID id,
        UUID bookingId,
        String reviewerName,
        String serviceTitle,
        int rating,
        String comment
) {}
