package at.hcw.serviceratebackend.dto;

import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID bookingId,
        String reviewerName,
        String serviceTitle,
        Integer rating,
        String comment
) {}
