package at.hcw.serviceratebackend.security;

import at.hcw.serviceratebackend.config.JwtUtil;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private BookingRepository bookingRepository;

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customer;
    private User provider;
    private User admin;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
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
    void manipulatedAdminClaimDoesNotOverrideCustomerRoleFromDatabase() throws Exception {
        String tokenWithAdminClaim = jwtUtil.generateToken(customer.getEmail(), "ADMIN");

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAdminClaim))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/bookings/customer/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAdminClaim))
                .andExpect(status().isOk());
    }

    @Test
    void databaseRoleChangeTakesEffectWithoutIssuingNewToken() throws Exception {
        String tokenWithProviderClaim = jwtUtil.generateToken(provider.getEmail(), "PROVIDER");
        provider.setAccountType("CUSTOMER");
        userRepository.saveAndFlush(provider);

        mockMvc.perform(get("/api/bookings/provider/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithProviderClaim))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/bookings/customer/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithProviderClaim))
                .andExpect(status().isOk());
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
    void providerProfileUpdateCannotReplaceVerifiedPayPalReceiver() throws Exception {
        provider.setPaypalMerchantId("verified-merchant");
        provider.setPaypalEmail("verified@example.com");
        userRepository.saveAndFlush(provider);

        mockMvc.perform(put("/api/users/{id}", provider.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paypalMerchantId": "attacker-merchant",
                                  "paypalEmail": "attacker@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "PayPal-Zahlungsempfänger dürfen nur über das verifizierte PayPal-Onboarding geändert werden."
                ));

        mockMvc.perform(put("/api/users/{id}", provider.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paypalMerchantId": "attacker-merchant"
                                }
                                """))
                .andExpect(status().isForbidden());

        User unchanged = userRepository.findById(provider.getId()).orElseThrow();
        assertThat(unchanged.getPaypalMerchantId()).isEqualTo("verified-merchant");
        assertThat(unchanged.getPaypalEmail()).isEqualTo("verified@example.com");
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
    void removedMarkPaidRouteRejectsEveryRoleAndNeverChangesPaymentState() throws Exception {
        ServiceOffering offering = saveService(provider, "Service");
        Booking booking = saveUnpaidBooking(customer, offering);

        mockMvc.perform(post("/api/bookings/{id}/mark-paid", booking.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings/{id}/mark-paid", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());
        // Wiederholung desselben Requests darf ebenfalls keinen Seiteneffekt erzeugen.
        mockMvc.perform(post("/api/bookings/{id}/mark-paid", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings/{id}/mark-paid", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings/{id}/mark-paid", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());

        Booking unchanged = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(unchanged.getPaymentStatus()).isEqualTo("UNPAID");
        assertThat(unchanged.getSettlementStatus()).isEqualTo("NOT_READY");
        assertThat(unchanged.getPaidAt()).isNull();
        assertThat(unchanged.getPaymentNote()).isNull();
    }

    @Test
    void securityHeadersArePresentOnResponses() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void providerCanUpdateAndDeleteOwnService() throws Exception {
        ServiceOffering offering = saveService(provider, "Original");

        mockMvc.perform(put("/api/services/{id}", offering.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Aktualisiert")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Aktualisiert"));

        mockMvc.perform(delete("/api/services/{id}", offering.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider)))
                .andExpect(status().isOk());

        assertThat(serviceOfferingRepository.existsById(offering.getId())).isFalse();
    }

    @Test
    void foreignProviderCannotUpdateOrDeleteService() throws Exception {
        User otherProvider = saveUser("other-provider@example.com", "PROVIDER", "ACTIVE");
        ServiceOffering offering = saveService(provider, "Original");

        mockMvc.perform(put("/api/services/{id}", offering.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherProvider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Manipuliert")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dieser Service gehört nicht zu diesem Anbieter."));

        mockMvc.perform(delete("/api/services/{id}", offering.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherProvider)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dieser Service gehört nicht zu diesem Anbieter."));

        ServiceOffering unchanged = serviceOfferingRepository.findById(offering.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Original");
    }

    @Test
    void customerCannotUpdateOrDeleteService() throws Exception {
        ServiceOffering offering = saveService(provider, "Original");

        mockMvc.perform(put("/api/services/{id}", offering.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Manipuliert")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/services/{id}", offering.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());

        assertThat(serviceOfferingRepository.existsById(offering.getId())).isTrue();
    }

    @Test
    void anonymousUserCannotUpdateOrDeleteService() throws Exception {
        ServiceOffering offering = saveService(provider, "Original");

        mockMvc.perform(put("/api/services/{id}", offering.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Manipuliert")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/services/{id}", offering.getId()))
                .andExpect(status().isForbidden());

        assertThat(serviceOfferingRepository.existsById(offering.getId())).isTrue();
    }

    @Test
    void ownerGetsClientErrorForMissingService() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(put("/api/services/{id}", missingId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Aktualisiert")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Service nicht gefunden"));

        mockMvc.perform(delete("/api/services/{id}", missingId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(provider)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Service nicht gefunden"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getEmail(), user.getAccountType());
    }

    private ServiceOffering saveService(User owner, String title) {
        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setProvider(owner);
        offering.setTitle(title);
        offering.setDescription("Beschreibung");
        offering.setCategory("REPAIR");
        offering.setPrice(80.0);
        offering.setEstimatedHours(2.0);
        offering.setDeliverableType("ON_SITE");
        offering.setStatus("ACTIVE");
        return serviceOfferingRepository.saveAndFlush(offering);
    }

    private Booking saveUnpaidBooking(User bookingCustomer, ServiceOffering offering) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(bookingCustomer);
        booking.setServiceOffering(offering);
        booking.setServiceDate(OffsetDateTime.now().plusDays(1));
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStatus("ACCEPTED");
        booking.setPaymentStatus("UNPAID");
        booking.setSettlementStatus("NOT_READY");
        return bookingRepository.saveAndFlush(booking);
    }

    private String updateJson(String title) {
        return """
                {
                  "title": "%s",
                  "description": "Aktualisierte Beschreibung",
                  "category": "REPAIR",
                  "price": 90.0,
                  "estimatedHours": 3.0,
                  "imageUrls": [],
                  "deliverableType": "ON_SITE"
                }
                """.formatted(title);
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
