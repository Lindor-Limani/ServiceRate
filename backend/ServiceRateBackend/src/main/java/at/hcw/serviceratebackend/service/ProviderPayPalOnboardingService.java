package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.PayPalOnboardingLinkResponse;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ProviderPayPalOnboardingService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int STATE_BYTES = 32;
    private static final int STATE_TTL_MINUTES = 15;
    private static final String INVALID_STATE_MESSAGE = PayPalOnboardingStateService.INVALID_STATE_MESSAGE;

    private final UserRepository userRepository;
    private final PayPalService payPalService;
    private final UserService userService;
    private final PayPalOnboardingStateService onboardingStateService;

    @Transactional
    public PayPalOnboardingLinkResponse createOnboardingLink(String providerEmail) {
        User provider = findProvider(providerEmail);
        String onboardingState = newOnboardingState();
        PayPalService.PayPalReferral referral = payPalService.createSellerOnboardingLink(provider, onboardingState);
        provider.setPaypalReferralSelfUrl(referral.selfUrl());
        provider.setPaypalOnboardingStatus("LINK_CREATED");
        provider.setPaypalOnboardingStateHash(hashState(onboardingState));
        provider.setPaypalOnboardingStateExpiresAt(OffsetDateTime.now().plusMinutes(STATE_TTL_MINUTES));
        userRepository.save(provider);
        return new PayPalOnboardingLinkResponse(referral.actionUrl(), referral.selfUrl());
    }

    public UserResponse completeOnboarding(String providerEmail, String onboardingState) {
        if (onboardingState == null || onboardingState.isBlank() || onboardingState.length() > 128) {
            throw new IllegalArgumentException(INVALID_STATE_MESSAGE);
        }
        onboardingStateService.consume(
                providerEmail,
                hashState(onboardingState),
                OffsetDateTime.now()
        );
        return refreshOnboardingStatus(providerEmail);
    }

    @Transactional
    public UserResponse refreshOnboardingStatus(String providerEmail) {
        User provider = findProvider(providerEmail);
        PayPalService.PayPalSellerStatus status;
        try {
            if (provider.getPaypalMerchantId() != null && !provider.getPaypalMerchantId().isBlank()) {
                status = payPalService.getSellerOnboardingStatusByMerchantId(provider.getPaypalMerchantId());
            } else {
                status = payPalService.getSellerOnboardingStatusByTrackingId(provider.getId().toString());
            }
        } catch (IllegalStateException e) {
            status = payPalService.getSellerOnboardingStatus(provider.getPaypalReferralSelfUrl());
        }
        return applyOnboardingResult(provider, status);
    }

    private UserResponse applyOnboardingResult(User provider, PayPalService.PayPalSellerStatus status) {
        if (status.merchantIdInPayPal() != null && !status.merchantIdInPayPal().isBlank()) {
            provider.setPaypalMerchantId(status.merchantIdInPayPal().trim());
        }
        if (status.permissionsGranted() != null) {
            provider.setPaypalPermissionsGranted(status.permissionsGranted());
        }
        if (status.isEmailConfirmed() != null) {
            provider.setPaypalEmailConfirmed(status.isEmailConfirmed());
        }

        boolean hasPayPalReceiver = (provider.getPaypalMerchantId() != null && !provider.getPaypalMerchantId().isBlank())
                || (provider.getPaypalEmail() != null && !provider.getPaypalEmail().isBlank());
        boolean connected = hasPayPalReceiver
                && Boolean.TRUE.equals(provider.getPaypalPermissionsGranted())
                && Boolean.TRUE.equals(provider.getPaypalEmailConfirmed());

        if (connected) {
            provider.setPaypalOnboardingStatus("CONNECTED");
        } else if (hasPayPalReceiver) {
            provider.setPaypalOnboardingStatus("ACTION_REQUIRED");
        } else {
            provider.setPaypalOnboardingStatus("RETURNED_INCOMPLETE");
        }

        User saved = userRepository.save(provider);
        return userService.getById(saved.getId());
    }

    private User findProvider(String email) {
        User provider = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Provider nicht gefunden"));
        if (!"PROVIDER".equals(provider.getAccountType())) {
            throw new IllegalArgumentException("Diese Aktion ist nur fuer Provider erlaubt.");
        }
        return provider;
    }

    private String newOnboardingState() {
        byte[] bytes = new byte[STATE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashState(String state) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", e);
        }
    }
}
