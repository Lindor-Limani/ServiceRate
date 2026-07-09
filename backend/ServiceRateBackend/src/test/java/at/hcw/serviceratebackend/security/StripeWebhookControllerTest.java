package at.hcw.serviceratebackend.security;

import at.hcw.serviceratebackend.controller.StripeWebhookController;
import at.hcw.serviceratebackend.model.common.exception.GlobalExceptionHandler;
import at.hcw.serviceratebackend.service.StripeConnectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripeConnectService stripeConnectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StripeWebhookController(stripeConnectService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void webhookDelegatesPayloadAndSignatureToStripeService() throws Exception {
        String payload = "{\"type\":\"checkout.session.completed\"}";

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", "test-signature")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());

        verify(stripeConnectService).handleWebhook(payload, "test-signature");
    }

    @Test
    void webhookReturnsBadRequestWhenSignatureVerificationFailsInService() throws Exception {
        String payload = "{\"type\":\"checkout.session.completed\"}";
        doThrow(new IllegalArgumentException("Ungültige Stripe-Signatur"))
                .when(stripeConnectService)
                .handleWebhook(payload, "bad-signature");

        mockMvc.perform(post("/api/stripe/webhook")
                .header("Stripe-Signature", "bad-signature")
                .contentType("application/json")
                .content(payload))
                .andExpect(status().isBadRequest());
    }
}
