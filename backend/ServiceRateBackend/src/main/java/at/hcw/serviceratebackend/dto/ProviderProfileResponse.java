package at.hcw.serviceratebackend.dto;

import java.util.List;
import java.util.UUID;

public record ProviderProfileResponse(
        UUID id,
        String name,
        String status,
        int serviceCount,
        double averageRating,
        long reviewCount,
        int trustScore,
        List<String> categories,
        List<ServiceOfferingResponse> services
) {}
