package at.hcw.serviceratebackend.dto;

public record PayPalOnboardingReturnRequest(
        String merchantIdInPayPal,
        String paypalEmail,
        Boolean permissionsGranted,
        String accountStatus,
        Boolean consentStatus,
        Boolean isEmailConfirmed
) {}
