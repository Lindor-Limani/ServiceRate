package at.hcw.serviceratebackend.dto;

import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
        @Email String email,
        String password,
        String firstName,
        String lastName,
        String profileImageUrl,
        String payoutIban,
        String paypalMerchantId,
        String paypalEmail,
        String accountType,
        String status
) {
}
