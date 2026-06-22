package at.hcw.serviceratebackend.dto;

public record AdminStatsResponse(
        long users,
        long providers,
        long customers,
        long admins,
        long services,
        long bookings,
        long reviews,
        double averageRating,
        long openBookings,
        long completedBookings,
        long cancelledBookings,
        long openReports,
        double paidRevenue
) {}
