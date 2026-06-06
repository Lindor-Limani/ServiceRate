package at.hcw.serviceratebackend.dto;

import java.util.UUID;

public record ServiceOfferingResponse(
        UUID id,
        String providerName, // Z.B. "Matej Deronja"
        String title,
        String description,
        String category,
        Double price,
        String status
) {}