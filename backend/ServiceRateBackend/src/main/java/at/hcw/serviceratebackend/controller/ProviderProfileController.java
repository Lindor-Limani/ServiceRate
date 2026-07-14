package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.ProviderProfileResponse;
import at.hcw.serviceratebackend.dto.PayPalOnboardingLinkResponse;
import at.hcw.serviceratebackend.dto.StripeOnboardingLinkResponse;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.service.ImageResource;
import at.hcw.serviceratebackend.service.ProviderPayPalOnboardingService;
import at.hcw.serviceratebackend.service.ProviderProfileService;
import at.hcw.serviceratebackend.service.StripeConnectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderProfileController {

    private final ProviderProfileService providerProfileService;
    private final ProviderPayPalOnboardingService payPalOnboardingService;
    private final StripeConnectService stripeConnectService;

    @GetMapping("/{providerId}")
    public ProviderProfileResponse getProviderProfile(@PathVariable UUID providerId) {
        return providerProfileService.getProviderProfile(providerId);
    }

    @GetMapping("/{providerId}/avatar")
    public ResponseEntity<byte[]> getProviderAvatar(@PathVariable UUID providerId) {
        return providerProfileService.getProviderAvatar(providerId)
                .map(this::imageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/me/paypal/onboarding-link")
    public PayPalOnboardingLinkResponse createPayPalOnboardingLink(Authentication authentication) {
        return payPalOnboardingService.createOnboardingLink((String) authentication.getPrincipal());
    }

    @PostMapping("/me/paypal/onboarding-status")
    public UserResponse refreshPayPalOnboardingStatus(Authentication authentication) {
        return payPalOnboardingService.refreshOnboardingStatus((String) authentication.getPrincipal());
    }

    @PostMapping("/me/stripe/onboarding-link")
    public StripeOnboardingLinkResponse createStripeOnboardingLink(Authentication authentication) {
        return stripeConnectService.createOnboardingLink((String) authentication.getPrincipal());
    }

    @PostMapping("/me/stripe/onboarding-status")
    public UserResponse refreshStripeOnboardingStatus(Authentication authentication) {
        return stripeConnectService.refreshOnboardingStatus((String) authentication.getPrincipal());
    }

    private ResponseEntity<byte[]> imageResponse(ImageResource image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(image.bytes());
    }
}
