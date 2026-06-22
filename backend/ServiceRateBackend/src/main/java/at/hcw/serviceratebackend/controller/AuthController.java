package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.config.JwtUtil;
import at.hcw.serviceratebackend.dto.CreateUserRequest;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import at.hcw.serviceratebackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final UserService userService; // um User anzulegen
    private final PasswordEncoder passwordEncoder; //um Hashes zu vergleichen
    private final JwtUtil jwtUtil;

    @Value("${app.frontend-base-url:http://localhost:5500}")
    private String frontendBaseUrl;

    public record LoginRequest(String email, String password) {}
    public record ForgotPasswordRequest(String email) {}
    public record ResetPasswordRequest(String token, String newPassword) {}
    public record ResendVerificationRequest(String email) {}

    // --- REGISTRIERUNG ---
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.badRequest().body("E-Mail existiert bereits!");
        }
        var created = userService.create(request);
        return ResponseEntity.ok(Map.of(
                "user", created,
                "message", "Konto erstellt. Bitte verifiziere deine E-Mail-Adresse ueber den Link in der Mail."
        ));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        String accountType = userService.verifyEmail(token);
        String page = "PROVIDER".equals(accountType) ? "provider-dashboard.html" : "customer-app.html";
        URI redirect = URI.create(UriComponentsBuilder
                .fromUriString(frontendBaseUrl + "/" + page)
                .queryParam("verified", "true")
                .toUriString());
        return ResponseEntity.status(HttpStatus.FOUND).location(redirect).build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.createPasswordResetToken(request.email());
        return ResponseEntity.ok(Map.of(
                "message", "Falls die E-Mail existiert und verifiziert ist, wurde ein Reset-Link versendet."
        ));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody ResendVerificationRequest request) {
        userService.resendVerificationMail(request.email());
        return ResponseEntity.ok(Map.of(
                "message", "Falls die E-Mail existiert und noch nicht verifiziert ist, wurde ein neuer Link versendet."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Passwort wurde aktualisiert."));
    }

    // ---LOGIN ---
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User existiert nicht.");
        }

        // Wir vergleichen das Klartext-Passwort aus dem Request mit dem Hash aus der DB
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Falsches Passwort.");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getAccountType());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", user.getId().toString(),
                "emailVerified", user.isEmailVerified()
        ));
    }
}
