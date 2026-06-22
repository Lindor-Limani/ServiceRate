package at.hcw.serviceratebackend.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminBookingResponse(
        UUID id,
        String customerName,
        String providerName,
        String serviceTitle,
        String status,
        String paymentStatus,
        LocalDate bookingDate,
        OffsetDateTime paidAt
) {}
