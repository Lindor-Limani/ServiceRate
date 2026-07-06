package at.hcw.serviceratebackend.dto;

public record PayPalOnboardingLinkResponse(
        String actionUrl,
        String selfUrl
) {}
