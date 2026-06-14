package at.hcw.serviceratebackend.dto;
import java.time.OffsetDateTime;
import java.util.UUID;
public record CreateBookingRequest(
        UUID customerId,
        UUID serviceOfferingId,
        OffsetDateTime serviceDate
) {}