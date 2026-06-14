package at.hcw.serviceratebackend.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
        UUID customerId,
        UUID serviceOfferingId,
        LocalDate bookingDate
) {}
