package at.hcw.serviceratebackend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        String reporterEmail,
        String targetType,
        UUID targetId,
        String reason,
        String details,
        String status,
        OffsetDateTime createdAt
) {}
