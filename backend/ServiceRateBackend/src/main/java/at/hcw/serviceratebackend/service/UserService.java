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

    // --- CREATE ---
    public UserResponse create(CreateUserRequest request) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        // Passwort wird sicher gehasht gespeichert
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setAccountType(request.accountType().toUpperCase());
        user.setStatus("ACTIVE");
        user.setEmailVerified(false);
        user.setEmailVerificationToken(UUID.randomUUID().toString());

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public boolean verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Ungültiger Verifizierungs-Link"));
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
        return true;
    }

    @Transactional
    public String createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Falls die E-Mail existiert, wurde ein Reset-Link erstellt."));
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiresAt(OffsetDateTime.now().plusMinutes(30));
        userRepository.save(user);
        return token;
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
        if (request.accountType() != null && !request.accountType().isBlank()) {
            user.setAccountType(request.accountType().toUpperCase(Locale.ROOT));
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

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getAccountType(),
                user.getStatus()
        );
    }
}
