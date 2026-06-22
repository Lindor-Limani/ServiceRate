package at.hcw.serviceratebackend.dto;

public record UpdateBookingWorkRequest(
        Double actualHours,
        String providerNotes
) {}
