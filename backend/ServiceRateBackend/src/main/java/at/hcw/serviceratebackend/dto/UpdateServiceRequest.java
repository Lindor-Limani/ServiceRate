package at.hcw.serviceratebackend.dto;

public record UpdateServiceRequest(
        String title,
        String description,
        String category,
        Double price,
        Double estimatedHours,
        String imageUrl,
        String deliverableType
) {}
