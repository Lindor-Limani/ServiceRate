package at.hcw.serviceratebackend.security;

import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.StripeWebhookEventRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import at.hcw.serviceratebackend.service.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "stripe.secret-key=sk_test_webhook_integration",
        "stripe.webhook-secret=whsec_webhook_integration"
})
class StripeWebhookIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_webhook_integration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StripeWebhookEventRepository stripeWebhookEventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private MailService mailService;

    @BeforeEach
    void setUp() {
        stripeWebhookEventRepository.deleteAll();
        bookingRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signedCheckoutReplayReturnsOkAndProducesExactlyOneEffect() throws Exception {
        Booking booking = saveCheckoutBooking(UUID.randomUUID());
        String payload = checkoutCompletedPayload("evt_checkout_once", booking.getId());
        String signature = signature(payload);

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getPaymentStatus()).isEqualTo("PAID");
        assertThat(persisted.getStripeCheckoutSessionId()).isEqualTo("cs_evt_checkout_once");
        assertThat(persisted.getPaidAt()).isNotNull();
        assertThat(stripeWebhookEventRepository.findAll())
                .singleElement()
                .extracting(event -> event.getEventId())
                .isEqualTo("evt_checkout_once");
        verify(mailService, times(1)).sendPaymentRecordedMail(argThat(changed ->
                booking.getId().equals(changed.getId()) && "PAID".equals(changed.getPaymentStatus())
        ));
    }

    @Test
    void invalidSignatureCreatesNoInboxEntryAndLeavesBookingUnchanged() throws Exception {
        Booking booking = saveCheckoutBooking(UUID.randomUUID());
        String payload = checkoutCompletedPayload("evt_bad_signature", booking.getId());

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", "t=1,v1=invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getPaymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(stripeWebhookEventRepository.count()).isZero();
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingEventIdIsRejectedBeforeAnyEffect() throws Exception {
        Booking booking = saveCheckoutBooking(UUID.randomUUID());
        String payload = checkoutCompletedPayload(null, booking.getId());

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo("CHECKOUT_CREATED");
        assertThat(stripeWebhookEventRepository.count()).isZero();
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failedBusinessProcessingDoesNotConsumeEventAndRetryCanSucceed() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String payload = checkoutCompletedPayload("evt_business_retry", bookingId);
        String signature = signature(payload);

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
        assertThat(stripeWebhookEventRepository.count()).isZero();

        Booking booking = saveCheckoutBooking(bookingId);
        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getPaymentStatus()).isEqualTo("PAID");
        assertThat(stripeWebhookEventRepository.existsByEventId("evt_business_retry")).isTrue();
        verify(mailService, times(1)).sendPaymentRecordedMail(argThat(changed -> bookingId.equals(changed.getId())));
    }

    private Booking saveCheckoutBooking(UUID bookingId) {
        User customer = saveUser("customer-" + bookingId + "@example.com", "CUSTOMER");
        User provider = saveUser("provider-" + bookingId + "@example.com", "PROVIDER");

        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setProvider(provider);
        offering.setTitle("Stripe Webhook Service");
        offering.setDescription("Beschreibung");
        offering.setCategory("REPAIR");
        offering.setPrice(80.0);
        offering.setStatus("ACTIVE");
        offering = serviceOfferingRepository.saveAndFlush(offering);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCustomer(customer);
        booking.setServiceOffering(offering);
        booking.setServiceDate(OffsetDateTime.now().plusDays(1));
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStatus("ACCEPTED");
        booking.setPaymentProvider("CARD");
        booking.setPaymentStatus("CHECKOUT_CREATED");
        booking.setStripeCheckoutSessionId("cs_original");
        booking.setSettlementStatus("STRIPE_DESTINATION_CHARGE_PENDING");
        return bookingRepository.saveAndFlush(booking);
    }

    private User saveUser(String email, String accountType) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("not-used-in-this-test");
        user.setFirstName(accountType);
        user.setLastName("User");
        user.setAccountType(accountType);
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private String checkoutCompletedPayload(String eventId, UUID bookingId) {
        String idProperty = eventId == null ? "" : "\"id\":\"" + eventId + "\",";
        String sessionId = eventId == null ? "missing_id" : eventId;
        return """
                {
                  %s
                  "object":"event",
                  "api_version":"2026-06-24.dahlia",
                  "created":%d,
                  "data":{"object":{
                    "id":"cs_%s",
                    "object":"checkout.session",
                    "metadata":{"booking_id":"%s"},
                    "payment_intent":null
                  }},
                  "livemode":false,
                  "pending_webhooks":1,
                  "request":{"id":null,"idempotency_key":null},
                  "type":"checkout.session.completed"
                }
                """.formatted(idProperty, Instant.now().getEpochSecond(), sessionId, bookingId).trim();
    }

    private String signature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
