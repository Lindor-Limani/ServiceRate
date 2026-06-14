package at.hcw.serviceratebackend.dto;
import java.util.UUID;
public record BookingResponse(
        UUID id,
        String customerName,
        String serviceTitle,
        String status
) {}