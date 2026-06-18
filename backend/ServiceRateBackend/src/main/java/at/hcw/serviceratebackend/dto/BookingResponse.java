package at.hcw.serviceratebackend.dto;

import java.time.LocalDate;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String customerName,
        String serviceTitle,
        String status,
        LocalDate bookingDate,
        ReviewResponse review
) {}
