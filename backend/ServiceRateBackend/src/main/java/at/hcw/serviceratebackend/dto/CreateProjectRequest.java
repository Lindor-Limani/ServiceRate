package at.hcw.serviceratebackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateProjectRequest(
        @NotNull UUID buyerOrganizationId,
        @NotNull UUID createdByUserId,
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String projectType,
        @NotBlank String workMode
) {
}
