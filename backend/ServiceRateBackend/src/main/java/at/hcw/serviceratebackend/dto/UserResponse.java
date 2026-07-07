package at.hcw.serviceratebackend.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String profileImageUrl,
        String payoutIban,
        String paypalMerchantId,
        String paypalEmail,
        String paypalOnboardingStatus,
        Boolean paypalPermissionsGranted,
        Boolean paypalEmailConfirmed,
        String stripeConnectedAccountId,
        String stripeOnboardingStatus,
        String accountType,
        String status
) {
}
