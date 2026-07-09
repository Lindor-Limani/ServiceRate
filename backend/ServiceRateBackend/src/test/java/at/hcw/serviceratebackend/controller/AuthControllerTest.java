package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.config.JwtUtil;
import at.hcw.serviceratebackend.dto.CreateUserRequest;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import at.hcw.serviceratebackend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void register_returnsCreatedUserAndMessageForValidRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        var request = new CreateUserRequest("new@example.com", "secret", "Ada", "Lovelace", "CUSTOMER");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userService.create(any(CreateUserRequest.class))).thenReturn(new UserResponse(
                userId,
                "new@example.com",
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null,
                "NOT_CONNECTED",
                null,
                null,
                null,
                "NOT_CONNECTED",
                "CUSTOMER",
                "ACTIVE"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "password": "secret",
                                  "firstName": "Ada",
                                  "lastName": "Lovelace",
                                  "accountType": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value("new@example.com"))
                .andExpect(jsonPath("$.message").value("Konto erstellt. Bitte verifiziere deine E-Mail-Adresse ueber den Link in der Mail."));
    }

    @Test
    void register_returnsBadRequestWhenEmailAlreadyExists() throws Exception {
        User existing = user("taken@example.com", "CUSTOMER", "ACTIVE");
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "taken@example.com",
                                  "password": "secret",
                                  "firstName": "Ada",
                                  "lastName": "Lovelace",
                                  "accountType": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("E-Mail existiert bereits!"));

        verify(userService, never()).create(any());
    }

    @Test
    void register_returnsValidationErrorForInvalidEmailAndBlankFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "",
                                  "firstName": "",
                                  "lastName": "User",
                                  "accountType": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(userService, never()).create(any());
    }

    @Test
    void login_returnsJwtAndUserMetadataForValidCredentials() throws Exception {
        User user = user("customer@example.com", "CUSTOMER", "ACTIVE");
        user.setEmailVerified(true);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtUtil.generateToken("customer@example.com", "CUSTOMER")).thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void login_returnsUnauthorizedForUnknownUserOrWrongPassword() throws Exception {
        User user = user("customer@example.com", "CUSTOMER", "ACTIVE");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "missing@example.com",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$").value("User existiert nicht."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "wrong"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$").value("Falsches Passwort."));
    }

    @Test
    void login_returnsForbiddenForInactiveUser() throws Exception {
        User user = user("customer@example.com", "CUSTOMER", "BLOCKED");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$").value("Dein Account wurde deaktiviert. Bitte kontaktiere den Support."));
    }

    private User user(String email, String accountType, String status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAccountType(accountType);
        user.setStatus(status);
        return user;
    }
}
