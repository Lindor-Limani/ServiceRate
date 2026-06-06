package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.config.JwtUtil;
import at.hcw.serviceratebackend.dto.CreateUserRequest;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import at.hcw.serviceratebackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final UserService userService; // um User anzulegen
    private final PasswordEncoder passwordEncoder; //um Hashes zu vergleichen
    private final JwtUtil jwtUtil;

    public record LoginRequest(String email, String password) {}

    // --- REGISTRIERUNG ---
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.badRequest().body("E-Mail existiert bereits!");
        }
        return ResponseEntity.ok(userService.create(request));
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
        return ResponseEntity.ok(Map.of("token", token));
    }
}