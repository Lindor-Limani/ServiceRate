package at.hcw.serviceratebackend.dto;

import java.util.UUID;
import java.util.List;

public record ServiceOfferingResponse(
        UUID id,
        UUID providerId,
        String providerName, // Z.B. "Matej Deronja"
        String providerProfileImageUrl,
        String title,
        String description,
        String category,
        Double price,
        Double estimatedHours,
        String imageUrl,
        String deliverableType,
        String status,
        String location,        // Ortsname, ermittelt aus der PLZ (Zippopotam.us)
        Double averageRating,   // Durchschnitt der Sterne (0.0 wenn noch keine Reviews)
        Long reviewCount,       // Anzahl der Bewertungen
        Integer trustScore,
        List<ReviewResponse> reviews
) {}
