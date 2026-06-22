package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.CreatePaymentMethodRequest;
import at.hcw.serviceratebackend.dto.PaymentMethodResponse;
import at.hcw.serviceratebackend.service.PaymentMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @GetMapping
    public List<PaymentMethodResponse> getMine(Authentication authentication) {
        return paymentMethodService.getMine((String) authentication.getPrincipal());
    }

    @PostMapping
    public PaymentMethodResponse create(@RequestBody CreatePaymentMethodRequest request, Authentication authentication) {
        return paymentMethodService.create((String) authentication.getPrincipal(), request);
    }

    @PutMapping("/{id}")
    public PaymentMethodResponse update(@PathVariable UUID id, @RequestBody CreatePaymentMethodRequest request, Authentication authentication) {
        return paymentMethodService.update((String) authentication.getPrincipal(), id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id, Authentication authentication) {
        paymentMethodService.delete((String) authentication.getPrincipal(), id);
    }
}
