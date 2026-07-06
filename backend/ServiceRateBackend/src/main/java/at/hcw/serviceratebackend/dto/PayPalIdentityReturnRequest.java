package at.hcw.serviceratebackend.dto;

public record PayPalIdentityReturnRequest(
        String code,
        String state,
        String redirectUri
) {}
