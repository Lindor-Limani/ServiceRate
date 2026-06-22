package at.hcw.serviceratebackend.dto;

import java.time.LocalDate;

public record CreateTimeEntryRequest(
        LocalDate workDate,
        Double hours,
        String note
) {}
