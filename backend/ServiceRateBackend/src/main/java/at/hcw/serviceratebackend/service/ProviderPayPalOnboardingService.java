package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.PayPalOnboardingLinkResponse;
import at.hcw.serviceratebackend.dto.PayPalIdentityReturnRequest;
import at.hcw.serviceratebackend.dto.PayPalOnboardingReturnRequest;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderPayPalOnboardingService {

    private final UserRepository userRepository;
    private final PayPalService payPalService;
    private final UserService userService;

    @Transactional
    public PayPalOnboardingLinkResponse createOnboardingLink(String providerEmail) {
        User provider = findProvider(providerEmail);
        PayPalService.PayPalReferral referral = payPalService.createSellerOnboardingLink(provider);
        provider.setPaypalReferralSelfUrl(referral.selfUrl());
        provider.setPaypalOnboardingStatus("LINK_CREATED");
        userRepository.save(provider);
        return new PayPalOnboardingLinkResponse(referral.actionUrl(), referral.selfUrl());
    }

    @Transactional
    public UserResponse completeIdentityOnboarding(String providerEmail, PayPalIdentityReturnRequest request) {
        User provider = findProvider(providerEmail);
        if (request.state() != null && !request.state().isBlank() && !provider.getId().toString().equals(request.state())) {
            throw new IllegalArgumentException("PayPal-Rueckkehr passt nicht zum eingeloggten Provider.");
        }
        PayPalService.PayPalIdentityProfile profile = payPalService.exchangeIdentityCode(request.code(), request.redirectUri());
        if (profile.accountId() != null && !profile.accountId().isBlank()) {
            provider.setPaypalMerchantId(profile.accountId());
        }
        if (profile.email() != null && !profile.email().isBlank()) {
            provider.setPaypalEmail(profile.email());
        }
        provider.setPaypalPermissionsGranted(true);
        provider.setPaypalEmailConfirmed(profile.emailConfirmed() == null || profile.emailConfirmed());
        provider.setPaypalOnboardingStatus("CONNECTED");
        User saved = userRepository.save(provider);
        return userService.getById(saved.getId());
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
        return applyOnboardingResult(provider, new PayPalOnboardingReturnRequest(
                status.merchantIdInPayPal(),
                null,
                status.permissionsGranted(),
                status.accountStatus(),
                status.consentStatus(),
                status.isEmailConfirmed()
        ));
    }

    @Transactional
    public UserResponse completeOnboarding(String providerEmail, PayPalOnboardingReturnRequest request) {
        User provider = findProvider(providerEmail);
        return applyOnboardingResult(provider, request);
    }

    private UserResponse applyOnboardingResult(User provider, PayPalOnboardingReturnRequest request) {
        if (request.merchantIdInPayPal() != null && !request.merchantIdInPayPal().isBlank()) {
            provider.setPaypalMerchantId(request.merchantIdInPayPal().trim());
        }
        if (request.paypalEmail() != null && !request.paypalEmail().isBlank()) {
            provider.setPaypalEmail(request.paypalEmail().trim());
        }
        if (request.permissionsGranted() != null) {
            provider.setPaypalPermissionsGranted(request.permissionsGranted());
        }
        if (request.isEmailConfirmed() != null) {
            provider.setPaypalEmailConfirmed(request.isEmailConfirmed());
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
}
