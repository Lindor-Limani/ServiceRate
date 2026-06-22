package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateUserRequest;
import at.hcw.serviceratebackend.dto.UpdateUserRequest;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.model.common.enums.UserStatus;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    // --- CREATE ---
    public UserResponse create(CreateUserRequest request) {
        String accountType = normalizeAllowedPublicAccountType(request.accountType());
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        // Passwort wird sicher gehasht gespeichert
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setAccountType(accountType);
        user.setStatus("ACTIVE");
        user.setEmailVerified(false);
        user.setEmailVerificationToken(UUID.randomUUID().toString());
        user.setEmailVerificationExpiresAt(OffsetDateTime.now().plusMinutes(10));

        User saved = userRepository.save(user);
        mailService.sendVerificationMail(saved);
        return toResponse(saved);
    }

    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Ungültiger Verifizierungs-Link"));
        if (user.getEmailVerificationExpiresAt() == null || user.getEmailVerificationExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Verifizierungs-Link ist abgelaufen. Bitte fordere einen neuen Link an.");
        }
        String accountType = user.getAccountType();
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        userRepository.save(user);
        return accountType;
    }

    @Transactional
    public String createPasswordResetToken(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty() || !maybeUser.get().isEmailVerified()) {
            return null;
        }
        User user = maybeUser.get();
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiresAt(OffsetDateTime.now().plusMinutes(30));
        userRepository.save(user);
        mailService.sendPasswordResetMail(user, token);
        return token;
    }

    @Transactional
    public void resendVerificationMail(String email) {
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    user.setEmailVerificationToken(UUID.randomUUID().toString());
                    user.setEmailVerificationExpiresAt(OffsetDateTime.now().plusMinutes(10));
                    mailService.sendVerificationMail(userRepository.save(user));
                });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Ungültiger Reset-Link"));
        if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Reset-Link ist abgelaufen");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
    }

    // --- READ ONE ---
    public UserResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    // --- UPDATE (Profil) ---
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findOrThrow(id);

        if (request.email() != null && !request.email().isBlank()) {
            Optional<User> existing = userRepository.findByEmail(request.email());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw new IllegalArgumentException("E-Mail existiert bereits");
            }
            user.setEmail(request.email());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.profileImageUrl() != null) user.setProfileImageUrl(trimOrNull(request.profileImageUrl()));
        if (request.payoutIban() != null) user.setPayoutIban(trimOrNull(request.payoutIban()));
        if (request.accountType() != null && !request.accountType().isBlank()) {
            user.setAccountType(normalizeAllowedPublicAccountType(request.accountType()));
        }
        if (request.status() != null && !request.status().isBlank()) {
            try {
                UserStatus.valueOf(request.status().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Ungültiger Benutzerstatus");
            }
            user.setStatus(request.status().toUpperCase(Locale.ROOT));
        }

        return toResponse(userRepository.save(user));
    }

    // --- DELETE (inkl. abhängiger Buchungen & Services) ---
    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User nicht gefunden");
        }
        // Buchungen, in denen der User Kunde ist
        bookingRepository.deleteAll(bookingRepository.findByCustomer_Id(id));
        // Buchungen auf den eigenen Angeboten
        bookingRepository.deleteAll(bookingRepository.findByServiceOffering_Provider_Id(id));
        // Eigene Service-Angebote
        serviceOfferingRepository.deleteAll(serviceOfferingRepository.findByProviderId(id));
        // Zuletzt den User selbst
        userRepository.deleteById(id);
    }

    // Hilfsmethode für den Self-Service-Check im Controller (Token-Subject -> User-ID)
    public Optional<UUID> findIdByEmail(String email) {
        return userRepository.findByEmail(email).map(User::getId);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));
    }

    private String normalizeAllowedPublicAccountType(String accountType) {
        String normalized = accountType == null ? "" : accountType.trim().toUpperCase(Locale.ROOT);
        if (!"CUSTOMER".equals(normalized) && !"PROVIDER".equals(normalized)) {
            throw new IllegalArgumentException("accountType darf nur CUSTOMER oder PROVIDER sein");
        }
        return normalized;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfileImageUrl(),
                user.getPayoutIban(),
                user.getAccountType(),
                user.getStatus()
        );
    }

    private String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
