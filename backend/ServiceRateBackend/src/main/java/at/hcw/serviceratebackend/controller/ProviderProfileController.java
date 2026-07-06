package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.ProviderProfileResponse;
import at.hcw.serviceratebackend.dto.PayPalOnboardingLinkResponse;
import at.hcw.serviceratebackend.dto.PayPalIdentityReturnRequest;
import at.hcw.serviceratebackend.dto.PayPalOnboardingReturnRequest;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.service.ProviderPayPalOnboardingService;
import at.hcw.serviceratebackend.service.ProviderProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderProfileController {

    private final ProviderProfileService providerProfileService;
    private final ProviderPayPalOnboardingService payPalOnboardingService;

    @GetMapping("/{providerId}")
    public ProviderProfileResponse getProviderProfile(@PathVariable UUID providerId) {
        return providerProfileService.getProviderProfile(providerId);
    }

    @PostMapping("/me/paypal/onboarding-link")
    public PayPalOnboardingLinkResponse createPayPalOnboardingLink(Authentication authentication) {
        return payPalOnboardingService.createOnboardingLink((String) authentication.getPrincipal());
    }

    @PostMapping("/me/paypal/onboarding-return")
    public UserResponse completePayPalOnboarding(@RequestBody PayPalOnboardingReturnRequest request, Authentication authentication) {
        return payPalOnboardingService.completeOnboarding((String) authentication.getPrincipal(), request);
    }

    @PostMapping("/me/paypal/identity-return")
    public UserResponse completePayPalIdentityOnboarding(@RequestBody PayPalIdentityReturnRequest request, Authentication authentication) {
        return payPalOnboardingService.completeIdentityOnboarding((String) authentication.getPrincipal(), request);
    }

    @PostMapping("/me/paypal/onboarding-status")
    public UserResponse refreshPayPalOnboardingStatus(Authentication authentication) {
        return payPalOnboardingService.refreshOnboardingStatus((String) authentication.getPrincipal());
    }
}
