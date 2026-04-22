package at.hcw.serviceratebackend.dto;

import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String title,
        String description,
        String projectType,
        String workMode,
        String status
) {
}
