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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        String payload = checkoutCompletedPayload("evt_checkout_once", booking);
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
        assertThat(persisted.getStripeCheckoutSessionId()).isEqualTo(booking.getStripeCheckoutSessionId());
        assertThat(persisted.getStripePaymentIntentId()).isEqualTo(booking.getStripePaymentIntentId());
        assertThat(persisted.getStripePaymentMethodId()).isEqualTo("pm_test");
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
        String payload = checkoutCompletedPayload("evt_bad_signature", booking);

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
        String payload = checkoutCompletedPayload(null, booking);

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
        String sessionId = stripeId("cs", bookingId);
        String paymentIntentId = stripeId("pi", bookingId);
        String payload = checkoutCompletedPayload(
                "evt_business_retry", bookingId, sessionId, paymentIntentId
        );
        String signature = signature(payload);

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
        assertThat(stripeWebhookEventRepository.count()).isZero();

        Booking booking = saveCheckoutBooking(bookingId, sessionId, paymentIntentId);
        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getPaymentStatus()).isEqualTo("PAID");
        assertThat(stripeWebhookEventRepository.existsByEventId("evt_business_retry")).isTrue();
        verify(mailService, times(1)).sendPaymentRecordedMail(argThat(changed -> bookingId.equals(changed.getId())));
    }

    @Test
    void foreignCheckoutSessionIsRejectedWithoutMutationOrConsumedEvent() throws Exception {
        Booking booking = saveCheckoutBooking(UUID.randomUUID());
        String payload = checkoutCompletedPayload(
                "evt_foreign_session",
                booking.getId(),
                "cs_foreign",
                booking.getStripePaymentIntentId()
        );

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertCheckoutUnchanged(booking);
        assertThat(stripeWebhookEventRepository.existsByEventId("evt_foreign_session")).isFalse();
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void foreignPaymentIntentIsRejectedWithoutMutationOrConsumedEvent() throws Exception {
        Booking booking = saveCheckoutBooking(UUID.randomUUID());
        String payload = checkoutCompletedPayload(
                "evt_foreign_intent",
                booking.getId(),
                booking.getStripeCheckoutSessionId(),
                "pi_foreign"
        );

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertCheckoutUnchanged(booking);
        assertThat(stripeWebhookEventRepository.existsByEventId("evt_foreign_intent")).isFalse();
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completedWebhookRejectsMissingOrMismatchedFinancialAndDestinationValues() throws Exception {
        String[][] mutations = {
                {"\"amount_total\":8000,", ""},
                {"\"amount_total\":8000", "\"amount_total\":7999"},
                {"\"currency\":\"eur\",\n    \"payment_status\"", "\"payment_status\""},
                {"\"currency\":\"eur\",\n    \"payment_status\"", "\"currency\":\"\",\n    \"payment_status\""},
                {"\"currency\":\"eur\",\n    \"payment_status\"", "\"currency\":\"usd\",\n    \"payment_status\""},
                {"\"payment_status\":\"paid\",", ""},
                {"\"payment_status\":\"paid\"", "\"payment_status\":\"unpaid\""},
                {"\"amount\":8000,", ""},
                {"\"amount\":8000", "\"amount\":7999"},
                {"\"amount_received\":8000,", ""},
                {"\"amount_received\":8000", "\"amount_received\":7999"},
                {"\"application_fee_amount\":800,", ""},
                {"\"application_fee_amount\":800", "\"application_fee_amount\":0"},
                {"\"application_fee_amount\":800", "\"application_fee_amount\":799"},
                {"\"currency\":\"eur\",\n      \"status\"", "\"status\""},
                {"\"currency\":\"eur\",\n      \"status\"", "\"currency\":\"\",\n      \"status\""},
                {"\"currency\":\"eur\",\n      \"status\"", "\"currency\":\"usd\",\n      \"status\""},
                {"\"status\":\"succeeded\",", ""},
                {"\"status\":\"succeeded\"", "\"status\":\"processing\""},
                {"\"transfer_data\":{\"destination\":\"acct_verified\"},", ""},
                {"\"transfer_data\":{\"destination\":\"acct_verified\"}", "\"transfer_data\":{\"destination\":\"\"}"},
                {"\"transfer_data\":{\"destination\":\"acct_verified\"}", "\"transfer_data\":{\"destination\":\"acct_foreign\"}"}
        };

        for (int index = 0; index < mutations.length; index++) {
            Booking booking = saveCheckoutBooking(UUID.randomUUID());
            String originalPayload = checkoutCompletedPayload("evt_financial_" + index, booking);
            String payload = originalPayload.replace(mutations[index][0], mutations[index][1]);
            assertThat(payload).as("payload mutation %s", index).isNotEqualTo(originalPayload);

            mockMvc.perform(post("/api/stripe/webhook")
                            .header("Stripe-Signature", signature(payload))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());

            assertCheckoutUnchanged(booking);
            assertThat(stripeWebhookEventRepository.existsByEventId("evt_financial_" + index)).isFalse();
        }

        assertThat(stripeWebhookEventRepository.count()).isZero();
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completedWebhookRejectsIncompleteCheckoutSnapshotWithoutConsumingEvent() throws Exception {
        Booking booking = saveCheckoutBooking(UUID.randomUUID());
        booking.setStripeExpectedApplicationFeeMinor(null);
        bookingRepository.saveAndFlush(booking);
        String payload = checkoutCompletedPayload("evt_missing_snapshot", booking);

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        assertCheckoutUnchanged(booking);
        assertThat(stripeWebhookEventRepository.existsByEventId("evt_missing_snapshot")).isFalse();
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void emptySessionAndPaymentIntentIdsAreRejected() throws Exception {
        Booking missingSession = saveCheckoutBooking(UUID.randomUUID());
        String missingSessionPayload = checkoutCompletedPayload(
                "evt_empty_session", missingSession.getId(), "", missingSession.getStripePaymentIntentId()
        );
        Booking missingIntent = saveCheckoutBooking(UUID.randomUUID());
        String missingIntentPayload = checkoutCompletedPayload(
                "evt_empty_intent", missingIntent.getId(), missingIntent.getStripeCheckoutSessionId(), ""
        );

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(missingSessionPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingSessionPayload))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(missingIntentPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingIntentPayload))
                .andExpect(status().isBadRequest());

        assertCheckoutUnchanged(missingSession);
        assertCheckoutUnchanged(missingIntent);
        assertThat(stripeWebhookEventRepository.count()).isZero();
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void wrongPaymentProviderAndInvalidStartingStatusAreRejected() throws Exception {
        Booking wrongProvider = saveCheckoutBooking(UUID.randomUUID());
        wrongProvider.setPaymentProvider("PAYPAL");
        bookingRepository.saveAndFlush(wrongProvider);
        String providerPayload = checkoutCompletedPayload("evt_wrong_provider", wrongProvider);

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(providerPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(providerPayload))
                .andExpect(status().isBadRequest());

        Booking invalidStatus = saveCheckoutBooking(UUID.randomUUID());
        invalidStatus.setPaymentStatus("UNPAID");
        bookingRepository.saveAndFlush(invalidStatus);
        String statusPayload = checkoutCompletedPayload("evt_invalid_status", invalidStatus);

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(statusPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isConflict());

        assertThat(bookingRepository.findById(wrongProvider.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo("CHECKOUT_CREATED");
        assertThat(bookingRepository.findById(invalidStatus.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo("UNPAID");
        assertThat(stripeWebhookEventRepository.count()).isZero();
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failedEventTransitionsOnlyCheckoutCreatedAndReplayIsSideEffectFree() throws Exception {
        Booking booking = saveCheckoutBooking(UUID.randomUUID());
        String payload = paymentFailedPayload("evt_failed_once", booking);
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
        assertThat(persisted.getPaymentStatus()).isEqualTo("FAILED");
        assertThat(persisted.getPaymentNote()).isEqualTo("Stripe-Kartenzahlung ist fehlgeschlagen.");
        assertThat(stripeWebhookEventRepository.findAll())
                .singleElement()
                .extracting(event -> event.getEventId())
                .isEqualTo("evt_failed_once");
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failedEventRejectsForeignPaymentIntentAndInvalidStartingStatus() throws Exception {
        Booking foreignIntent = saveCheckoutBooking(UUID.randomUUID());
        String foreignIntentPayload = paymentFailedPayload(
                "evt_failed_foreign_intent", foreignIntent.getId(), "pi_foreign"
        );

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(foreignIntentPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foreignIntentPayload))
                .andExpect(status().isBadRequest());

        Booking invalidStatus = saveCheckoutBooking(UUID.randomUUID());
        invalidStatus.setPaymentStatus("UNPAID");
        bookingRepository.saveAndFlush(invalidStatus);
        String invalidStatusPayload = paymentFailedPayload("evt_failed_invalid_status", invalidStatus);

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(invalidStatusPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidStatusPayload))
                .andExpect(status().isConflict());

        assertThat(bookingRepository.findById(foreignIntent.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo("CHECKOUT_CREATED");
        assertThat(bookingRepository.findById(invalidStatus.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo("UNPAID");
        assertThat(stripeWebhookEventRepository.count()).isZero();
        verify(mailService, never()).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bothOutOfOrderSequencesEndDeterministicallyPaid() throws Exception {
        Booking failedThenCompleted = saveCheckoutBooking(UUID.randomUUID());
        Booking completedThenFailed = saveCheckoutBooking(UUID.randomUUID());

        postSigned(paymentFailedPayload("evt_failed_first", failedThenCompleted), 200);
        postSigned(checkoutCompletedPayload("evt_completed_second", failedThenCompleted), 200);
        postSigned(checkoutCompletedPayload("evt_completed_first", completedThenFailed), 200);
        postSigned(paymentFailedPayload("evt_failed_second", completedThenFailed), 200);

        assertThat(bookingRepository.findById(failedThenCompleted.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo("PAID");
        assertThat(bookingRepository.findById(completedThenFailed.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo("PAID");
        assertThat(stripeWebhookEventRepository.count()).isEqualTo(4);
        verify(mailService, times(2)).sendPaymentRecordedMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void parallelCompletedAndFailedEventsAlwaysEndPaid() throws Exception {
        Booking booking = saveCheckoutBooking(UUID.randomUUID());
        String completedPayload = checkoutCompletedPayload("evt_parallel_completed", booking);
        String failedPayload = paymentFailedPayload("evt_parallel_failed", booking);
        String completedSignature = signature(completedPayload);
        String failedSignature = signature(failedPayload);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> completed = executor.submit(() -> postAfterBarrier(
                    ready, start, completedPayload, completedSignature
            ));
            Future<Integer> failed = executor.submit(() -> postAfterBarrier(
                    ready, start, failedPayload, failedSignature
            ));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(completed.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(failed.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo("PAID");
        assertThat(stripeWebhookEventRepository.count()).isEqualTo(2);
        verify(mailService, times(1)).sendPaymentRecordedMail(argThat(changed ->
                booking.getId().equals(changed.getId()) && "PAID".equals(changed.getPaymentStatus())
        ));
    }

    private Booking saveCheckoutBooking(UUID bookingId) {
        return saveCheckoutBooking(
                bookingId,
                stripeId("cs", bookingId),
                stripeId("pi", bookingId)
        );
    }

    private Booking saveCheckoutBooking(UUID bookingId, String sessionId, String paymentIntentId) {
        User customer = saveUser("customer-" + bookingId + "@example.com", "CUSTOMER");
        User provider = saveUser("provider-" + bookingId + "@example.com", "PROVIDER");

        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setProvider(provider);
        offering.setTitle("Stripe Webhook Service");
        offering.setDescription("Beschreibung");
        offering.setCategory("REPAIR");
        offering.setPrice(new BigDecimal("80.00"));
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
        booking.setStripeCheckoutSessionId(sessionId);
        booking.setStripePaymentIntentId(paymentIntentId);
        booking.setStripeExpectedAmountMinor(8000L);
        booking.setStripeExpectedApplicationFeeMinor(800L);
        booking.setStripeCurrencyCode("EUR");
        booking.setStripeConnectedAccountId("acct_verified");
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

    private String checkoutCompletedPayload(String eventId, Booking booking) {
        return checkoutCompletedPayload(
                eventId,
                booking.getId(),
                booking.getStripeCheckoutSessionId(),
                booking.getStripePaymentIntentId()
        );
    }

    private String checkoutCompletedPayload(
            String eventId,
            UUID bookingId,
            String sessionId,
            String paymentIntentId
    ) {
        String idProperty = eventId == null ? "" : "\"id\":\"" + eventId + "\",";
        return """
                {
                  %s
                  "object":"event",
                  "api_version":"2026-06-24.dahlia",
                  "created":%d,
                  "data":{"object":{
                    "id":"%s",
                    "object":"checkout.session",
                    "metadata":{"booking_id":"%s"},
                    "amount_total":8000,
                    "currency":"eur",
                    "payment_status":"paid",
                    "payment_intent":{
                      "id":"%s",
                      "object":"payment_intent",
                      "metadata":{"booking_id":"%s"},
                      "amount":8000,
                      "amount_received":8000,
                      "application_fee_amount":800,
                      "currency":"eur",
                      "status":"succeeded",
                      "transfer_data":{"destination":"acct_verified"},
                      "payment_method":"pm_test"
                    }
                  }},
                  "livemode":false,
                  "pending_webhooks":1,
                  "request":{"id":null,"idempotency_key":null},
                  "type":"checkout.session.completed"
                }
                """.formatted(
                idProperty,
                Instant.now().getEpochSecond(),
                sessionId,
                bookingId,
                paymentIntentId,
                bookingId
        ).trim();
    }

    private String paymentFailedPayload(String eventId, Booking booking) {
        return paymentFailedPayload(eventId, booking.getId(), booking.getStripePaymentIntentId());
    }

    private String paymentFailedPayload(String eventId, UUID bookingId, String paymentIntentId) {
        return """
                {
                  "id":"%s",
                  "object":"event",
                  "api_version":"2026-06-24.dahlia",
                  "created":%d,
                  "data":{"object":{
                    "id":"%s",
                    "object":"payment_intent",
                    "metadata":{"booking_id":"%s"},
                    "status":"requires_payment_method"
                  }},
                  "livemode":false,
                  "pending_webhooks":1,
                  "request":{"id":null,"idempotency_key":null},
                  "type":"payment_intent.payment_failed"
                }
                """.formatted(
                eventId,
                Instant.now().getEpochSecond(),
                paymentIntentId,
                bookingId
        ).trim();
    }

    private void assertCheckoutUnchanged(Booking original) {
        Booking persisted = bookingRepository.findById(original.getId()).orElseThrow();
        assertThat(persisted.getPaymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(persisted.getStripeCheckoutSessionId()).isEqualTo(original.getStripeCheckoutSessionId());
        assertThat(persisted.getStripePaymentIntentId()).isEqualTo(original.getStripePaymentIntentId());
        assertThat(persisted.getPaidAt()).isNull();
    }

    private void postSigned(String payload, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus));
    }

    private int postAfterBarrier(
            CountDownLatch ready,
            CountDownLatch start,
            String payload,
            String signature
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Paralleler Teststart fehlgeschlagen");
        }
        return mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String stripeId(String prefix, UUID bookingId) {
        return prefix + "_" + bookingId.toString().replace("-", "");
    }

    private String signature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
