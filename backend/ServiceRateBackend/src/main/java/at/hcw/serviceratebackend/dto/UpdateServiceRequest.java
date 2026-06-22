package at.hcw.serviceratebackend.dto;

import java.util.List;

public record UpdateServiceRequest(
        String title,
        String description,
        String category,
        Double price,
        Double estimatedHours,
        String imageUrl,
        List<String> imageUrls,
        String deliverableType
) {}
