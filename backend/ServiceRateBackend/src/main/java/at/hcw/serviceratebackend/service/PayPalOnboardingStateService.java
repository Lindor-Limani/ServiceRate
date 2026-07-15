package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class PayPalOnboardingStateService {

    static final String INVALID_STATE_MESSAGE = "PayPal-Onboarding-State ist ungültig oder abgelaufen.";

    private final UserRepository userRepository;

    @Transactional
    public void consume(String providerEmail, String stateHash, OffsetDateTime now) {
        User provider = userRepository.findByEmailForUpdate(providerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Provider nicht gefunden"));
        if (!"PROVIDER".equals(provider.getAccountType())) {
            throw new IllegalArgumentException("Diese Aktion ist nur fuer Provider erlaubt.");
        }

        boolean validHash = provider.getPaypalOnboardingStateHash() != null
                && MessageDigest.isEqual(
                        provider.getPaypalOnboardingStateHash().getBytes(StandardCharsets.US_ASCII),
                        stateHash.getBytes(StandardCharsets.US_ASCII)
                );
        boolean validExpiry = provider.getPaypalOnboardingStateExpiresAt() != null
                && provider.getPaypalOnboardingStateExpiresAt().isAfter(now);
        if (!validHash || !validExpiry) {
            throw new IllegalArgumentException(INVALID_STATE_MESSAGE);
        }

        provider.setPaypalOnboardingStateHash(null);
        provider.setPaypalOnboardingStateExpiresAt(null);
        userRepository.saveAndFlush(provider);
    }
}
