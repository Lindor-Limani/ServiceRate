package at.hcw.serviceratebackend.security;

import at.hcw.serviceratebackend.config.JwtUtil;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customer;
    private User provider;
    private User admin;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        customer = saveUser("customer@example.com", "CUSTOMER", "ACTIVE");
        provider = saveUser("provider@example.com", "PROVIDER", "ACTIVE");
        admin = saveUser("admin@example.com", "ADMIN", "ACTIVE");
    }

    @Test
    void protectedEndpoint_requiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/bookings/customer/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_rejectsMalformedBearerToken() throws Exception {
        mockMvc.perform(get("/api/bookings/customer/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer this.is.not.valid"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tamperedTokenDoesNotAuthenticateUser() throws Exception {
        String token = jwtUtil.generateToken(customer.getEmail(), customer.getAccountType());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/api/bookings/customer/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveUserWithOtherwiseValidTokenIsRejectedByJwtFilter() throws Exception {
        customer.setStatus("BLOCKED");
        userRepository.saveAndFlush(customer);

        mockMvc.perform(get("/api/bookings/customer/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Account wurde deaktiviert")));
    }

    @Test
    void customerCannotCallProviderOrAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/bookings/provider/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerCannotCallCustomerOrAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/bookings/customer/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCallAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").exists());
    }

    @Test
    void userCanReadOnlyOwnProfile() throws Exception {
        mockMvc.perform(get("/api/users/{id}", customer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(customer.getEmail()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/users/{id}", provider.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicSearchHandlesSqlInjectionLikeInputWithoutServerError() throws Exception {
        mockMvc.perform(get("/api/services")
                        .param("q", "' OR 1=1 --")
                        .param("category", "REPAIR")
                        .param("location", "../../etc/passwd")
                        .param("maxPrice", "100")
                        .param("minRating", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void invalidUuidPathReturnsClientErrorWithoutStackTraceLeak() throws Exception {
        mockMvc.perform(get("/api/users/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString("java.lang"))));
    }

    @Test
    void customerCannotSetPrivilegedRoleThroughProfileUpdate() throws Exception {
        mockMvc.perform(put("/api/users/{id}", customer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountType": "ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("accountType darf nur CUSTOMER oder PROVIDER sein"));
    }

    @Test
    void customerCannotPatchAdminStatusEvenWithDirectApiCall() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/status", provider.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void stripeWebhookRequiresSignatureHeader() throws Exception {
        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void securityHeadersArePresentOnResponses() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getEmail(), user.getAccountType());
    }

    private User saveUser(String email, String accountType, String status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFirstName(accountType);
        user.setLastName("User");
        user.setAccountType(accountType);
        user.setStatus(status);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }
}
