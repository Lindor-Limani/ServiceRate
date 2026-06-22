package at.hcw.serviceratebackend.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String customerName,
        String customerProfileImageUrl,
        String providerName,
        String providerProfileImageUrl,
        String serviceTitle,
        Double servicePrice,
        String status,
        LocalDate bookingDate,
        Double actualHours,
        String providerNotes,
        String customerNotes,
        String deliveryUrl,
        String deliveryLabel,
        OffsetDateTime deliveryExpiresAt,
        boolean deliveryAvailable,
        String paymentStatus,
        String checkoutUrl,
        String paymentProvider,
        String paymentNote,
        OffsetDateTime paidAt,
        List<TimeEntryResponse> timeEntries,
        ReviewResponse review
) {}
