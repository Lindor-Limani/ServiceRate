package at.hcw.serviceratebackend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateServiceRequest(
        UUID providerId, // Wer bietet das an? (Die ID, die du gerade in der Datenbank gesehen hast!)
        String title,
        String description,
        String category,
        BigDecimal price,
        Double estimatedHours,
        String imageUrl,
        List<String> imageUrls,
        String deliverableType,
        String zipCode   // Österreichische PLZ; wird über Zippopotam.us in einen Ortsnamen aufgelöst
) {}
