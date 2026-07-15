package at.hcw.serviceratebackend.dto;

import java.math.BigDecimal;
import java.util.List;

public record UpdateServiceRequest(
        String title,
        String description,
        String category,
        BigDecimal price,
        Double estimatedHours,
        String imageUrl,
        List<String> imageUrls,
        String deliverableType
) {}
