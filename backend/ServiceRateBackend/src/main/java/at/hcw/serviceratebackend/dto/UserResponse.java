package at.hcw.serviceratebackend.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String profileImageUrl,
        String accountType,
        String status
) {
}
