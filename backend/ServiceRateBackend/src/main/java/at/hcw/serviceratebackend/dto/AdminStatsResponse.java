package at.hcw.serviceratebackend.dto;

public record AdminStatsResponse(
        long users,
        long providers,
        long customers,
        long services,
        long bookings,
        long reviews
) {}
