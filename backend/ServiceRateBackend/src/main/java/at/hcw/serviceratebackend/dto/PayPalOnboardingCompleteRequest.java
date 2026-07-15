package at.hcw.serviceratebackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayPalOnboardingCompleteRequest(
        @NotBlank(message = "PayPal-Onboarding-State fehlt.")
        @Size(max = 128, message = "PayPal-Onboarding-State ist ungültig.")
        String state
) {
}
