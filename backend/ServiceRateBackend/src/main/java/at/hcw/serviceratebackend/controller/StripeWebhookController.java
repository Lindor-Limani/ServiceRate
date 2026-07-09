package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.service.StripeConnectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeConnectService stripeConnectService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("Stripe-Signature Header fehlt");
        }
        stripeConnectService.handleWebhook(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
