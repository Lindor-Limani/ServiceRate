package at.hcw.serviceratebackend.security;

import at.hcw.serviceratebackend.config.JwtUtil;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import at.hcw.serviceratebackend.service.ProviderPayPalOnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PayPalOnboardingSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ProviderPayPalOnboardingService onboardingService;

    private User customer;
    private User provider;
    private User admin;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        customer = saveUser("customer-state@example.com", "CUSTOMER", "ACTIVE");
        provider = saveUser("provider-state@example.com", "PROVIDER", "ACTIVE");
        admin = saveUser("admin-state@example.com", "ADMIN", "ACTIVE");
    }

    @Test
    void onboardingComplete_allowsOnlyAuthenticatedProviderAndPassesOnlyPrincipalAndState() throws Exception {
        String body = "{\"state\":\"bound-state\"}";

        mockMvc.perform(post("/api/providers/me/paypal/onboarding-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/providers/me/paypal/onboarding-complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/providers/me/paypal/onboarding-complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        verifyNoInteractions(onboardingService);

        mockMvc.perform(post("/api/providers/me/paypal/onboarding-complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(onboardingService).completeOnboarding(provider.getEmail(), "bound-state");
    }

    @Test
    void onboardingComplete_rejectsMissingOrOversizedStateBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/providers/me/paypal/onboarding-complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("state: PayPal-Onboarding-State fehlt."));
        mockMvc.perform(post("/api/providers/me/paypal/onboarding-complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"" + "x".repeat(129) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("state: PayPal-Onboarding-State ist ungültig."));

        verifyNoInteractions(onboardingService);
    }

    @Test
    void onboardingComplete_rejectsInactiveProvider() throws Exception {
        provider.setStatus("BLOCKED");
        userRepository.saveAndFlush(provider);

        mockMvc.perform(post("/api/providers/me/paypal/onboarding-complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"bound-state\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(onboardingService);
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
        user.setLastName("State");
        user.setAccountType(accountType);
        user.setStatus(status);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }
}
