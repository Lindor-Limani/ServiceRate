package at.hcw.serviceratebackend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID bookingId,
        String senderName,
        String content,
        OffsetDateTime createdAt
) {}
