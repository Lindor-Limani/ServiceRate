package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateUserRequest;
import at.hcw.serviceratebackend.dto.UpdateUserRequest;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailService mailService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, serviceOfferingRepository, bookingRepository, passwordEncoder, mailService);
    }

    @Test
    void create_hashesPassword_normalizesAccountTypeAndSendsVerificationMail() {
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.create(new CreateUserRequest(
                "new@example.com",
                "secret",
                "Ada",
                "Lovelace",
                " provider "
        ));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-secret");
        assertThat(saved.getAccountType()).isEqualTo("PROVIDER");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getEmailVerificationToken()).isNotBlank();
        assertThat(saved.getEmailVerificationExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(response.accountType()).isEqualTo("PROVIDER");
        verify(mailService).sendVerificationMail(saved);
    }

    @Test
    void create_rejectsAdminAccountTypeForPublicRegistration() {
        assertThatThrownBy(() -> userService.create(new CreateUserRequest(
                "admin@example.com",
                "secret",
                "Root",
                "User",
                "ADMIN"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("accountType darf nur CUSTOMER oder PROVIDER sein");

        verify(userRepository, never()).save(any());
        verify(mailService, never()).sendVerificationMail(any());
    }

    @Test
    void verifyEmail_marksUserVerifiedAndClearsToken() {
        User user = verifiedCandidate("CUSTOMER");
        when(userRepository.findByEmailVerificationToken("valid-token")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String accountType = userService.verifyEmail("valid-token");

        assertThat(accountType).isEqualTo("CUSTOMER");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationToken()).isNull();
        assertThat(user.getEmailVerificationExpiresAt()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_rejectsExpiredTokenWithoutSaving() {
        User user = verifiedCandidate("CUSTOMER");
        user.setEmailVerificationExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(userRepository.findByEmailVerificationToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.verifyEmail("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Verifizierungs-Link ist abgelaufen. Bitte fordere einen neuen Link an.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createPasswordResetToken_returnsNullForUnknownOrUnverifiedEmail() {
        User unverified = new User();
        unverified.setEmailVerified(false);
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(unverified));

        assertThat(userService.createPasswordResetToken("missing@example.com")).isNull();
        assertThat(userService.createPasswordResetToken("unverified@example.com")).isNull();

        verify(mailService, never()).sendPasswordResetMail(any(), any());
    }

    @Test
    void resetPassword_updatesHashAndClearsResetToken() {
        User user = new User();
        user.setPasswordResetToken("reset-token");
        user.setPasswordResetExpiresAt(OffsetDateTime.now().plusMinutes(5));
        when(userRepository.findByPasswordResetToken("reset-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");

        userService.resetPassword("reset-token", "new-secret");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getPasswordResetToken()).isNull();
        assertThat(user.getPasswordResetExpiresAt()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void update_rejectsDuplicateEmailAndInvalidStatus() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "old@example.com", "CUSTOMER");
        User otherUser = user(UUID.randomUUID(), "taken@example.com", "CUSTOMER");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> userService.update(userId, new UpdateUserRequest(
                "taken@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("E-Mail existiert bereits");

        assertThatThrownBy(() -> userService.update(userId, new UpdateUserRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "SLEEPING"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ungültiger Benutzerstatus");
    }

    private User verifiedCandidate(String accountType) {
        User user = user(UUID.randomUUID(), "user@example.com", accountType);
        user.setEmailVerified(false);
        user.setEmailVerificationToken("token");
        user.setEmailVerificationExpiresAt(OffsetDateTime.now().plusMinutes(5));
        return user;
    }

    private User user(UUID id, String email, String accountType) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAccountType(accountType);
        user.setStatus("ACTIVE");
        return user;
    }
}
