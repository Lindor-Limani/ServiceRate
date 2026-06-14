package at.hcw.serviceratebackend.dto;
import java.util.UUID;
public record CreateBookingRequest(
        UUID customerId,
        UUID serviceOfferingId
) {}