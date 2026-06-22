package at.hcw.serviceratebackend.dto;

public record CreatePaymentMethodRequest(
        String brand,
        String last4,
        String holderName,
        Integer expiryMonth,
        Integer expiryYear,
        String providerToken,
        Boolean defaultMethod
) {}
