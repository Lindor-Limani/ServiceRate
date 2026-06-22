package at.hcw.serviceratebackend.dto;

import java.util.UUID;

public record PaymentMethodResponse(
        UUID id,
        String brand,
        String last4,
        String holderName,
        Integer expiryMonth,
        Integer expiryYear,
        boolean defaultMethod
) {}
