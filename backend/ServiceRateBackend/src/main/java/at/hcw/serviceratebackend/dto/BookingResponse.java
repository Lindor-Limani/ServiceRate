package at.hcw.serviceratebackend.dto;
import java.time.OffsetDateTime;
import java.util.UUID;
public record BookingResponse(
        UUID id,
        String customerName,
        String serviceTitle,
        OffsetDateTime serviceDate,
        String status
) {}