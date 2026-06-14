package at.hcw.serviceratebackend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateBookingDateRequest (
    OffsetDateTime serviceDate
) {}
