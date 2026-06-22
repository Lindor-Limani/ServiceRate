package at.hcw.serviceratebackend.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TimeEntryResponse(
        UUID id,
        UUID bookingId,
        LocalDate workDate,
        Double hours,
        String note
) {}
