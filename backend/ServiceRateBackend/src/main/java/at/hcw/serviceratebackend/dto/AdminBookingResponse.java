package at.hcw.serviceratebackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminBookingResponse(
        UUID id,
        String customerName,
        String providerName,
        String serviceTitle,
        String status,
        String paymentStatus,
        String paymentProvider,
        BigDecimal grossAmount,
        BigDecimal platformFeeAmount,
        BigDecimal providerReceivableAmount,
        String settlementStatus,
        String settlementNote,
        LocalDate bookingDate,
        OffsetDateTime paidAt
) {}
