package at.hcw.serviceratebackend.dto;

import java.util.UUID;

public record ReportRequest(
        String targetType,
        UUID targetId,
        String reason,
        String details
) {}
