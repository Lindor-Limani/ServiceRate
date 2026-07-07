package at.hcw.serviceratebackend.dto;

public record StripeOnboardingLinkResponse(
        String accountId,
        String onboardingUrl
) {}
