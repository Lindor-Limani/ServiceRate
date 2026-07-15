package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderPayPalOnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PayPalService payPalService;

    @Mock
    private UserService userService;

    @Mock
    private PayPalOnboardingStateService onboardingStateService;

    @InjectMocks
    private ProviderPayPalOnboardingService service;

    @Test
    void createOnboardingLink_storesOnlyHashAndExpiryForCryptographicallyRandomState() {
        User provider = provider("provider@example.com", "PROVIDER");
        when(userRepository.findByEmail(provider.getEmail())).thenReturn(Optional.of(provider));
        when(payPalService.createSellerOnboardingLink(eq(provider), any(String.class)))
                .thenReturn(new PayPalService.PayPalReferral("https://paypal.example/action", "https://paypal.example/self"));
        when(userRepository.save(provider)).thenReturn(provider);

        OffsetDateTime before = OffsetDateTime.now();
        service.createOnboardingLink(provider.getEmail());

        var stateCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(payPalService).createSellerOnboardingLink(eq(provider), stateCaptor.capture());
        String rawState = stateCaptor.getValue();
        assertThat(rawState).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(provider.getPaypalOnboardingStateHash())
                .hasSize(64)
                .isNotEqualTo(rawState);
        assertThat(provider.getPaypalOnboardingStateExpiresAt())
                .isAfter(before.plusMinutes(14))
                .isBeforeOrEqualTo(OffsetDateTime.now().plusMinutes(15));
        assertThat(provider.getPaypalOnboardingStatus()).isEqualTo("LINK_CREATED");
        verify(userRepository).save(provider);
    }

    @Test
    void createOnboardingLink_replacesPreviousState() {
        User provider = provider("provider@example.com", "PROVIDER");
        when(userRepository.findByEmail(provider.getEmail())).thenReturn(Optional.of(provider));
        when(payPalService.createSellerOnboardingLink(eq(provider), any(String.class)))
                .thenReturn(new PayPalService.PayPalReferral("https://paypal.example/action", "https://paypal.example/self"));
        when(userRepository.save(provider)).thenReturn(provider);

        service.createOnboardingLink(provider.getEmail());
        String firstHash = provider.getPaypalOnboardingStateHash();
        service.createOnboardingLink(provider.getEmail());

        assertThat(provider.getPaypalOnboardingStateHash()).isNotEqualTo(firstHash);
        verify(payPalService, times(2)).createSellerOnboardingLink(eq(provider), any(String.class));
    }

    @Test
    void completeOnboarding_consumesBoundStateBeforeReadingStatusFromPayPal() {
        User provider = provider("provider@example.com", "PROVIDER");
        when(userRepository.findByEmail(provider.getEmail())).thenReturn(Optional.of(provider));
        when(payPalService.getSellerOnboardingStatusByTrackingId(provider.getId().toString()))
                .thenReturn(new PayPalService.PayPalSellerStatus("verified-merchant", true, "BUSINESS_ACCOUNT", true, true));
        when(userRepository.save(provider)).thenReturn(provider);

        service.completeOnboarding(provider.getEmail(), "valid-state");

        verify(onboardingStateService).consume(
                eq(provider.getEmail()),
                eq("5bef62c8763808dce7e9e42c8977277a28e586707343b41477722f97d0b7a4a5"),
                any(OffsetDateTime.class)
        );
        verify(payPalService).getSellerOnboardingStatusByTrackingId(provider.getId().toString());
        assertThat(provider.getPaypalMerchantId()).isEqualTo("verified-merchant");
        assertThat(provider.getPaypalOnboardingStatus()).isEqualTo("CONNECTED");
    }

    @Test
    void completeOnboarding_rejectsInvalidExpiredForeignOrReplayedStateWithoutCallingPayPal() {
        User provider = provider("provider@example.com", "PROVIDER");
        doThrow(new IllegalArgumentException("PayPal-Onboarding-State ist ungültig oder abgelaufen."))
                .when(onboardingStateService)
                .consume(eq(provider.getEmail()), any(String.class), any(OffsetDateTime.class));

        assertThatThrownBy(() -> service.completeOnboarding(provider.getEmail(), "invalid-state"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PayPal-Onboarding-State ist ungültig oder abgelaufen.");

        verifyNoInteractions(payPalService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void completeOnboarding_rejectsMissingStateBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.completeOnboarding("provider@example.com", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PayPal-Onboarding-State ist ungültig oder abgelaufen.");

        verifyNoInteractions(userRepository, payPalService, onboardingStateService);
    }

    @Test
    void completeOnboarding_rejectsUnknownProviderBeforeConsumingState() {
        doThrow(new IllegalArgumentException("Provider nicht gefunden"))
                .when(onboardingStateService)
                .consume(eq("missing@example.com"), any(String.class), any(OffsetDateTime.class));

        assertThatThrownBy(() -> service.completeOnboarding("missing@example.com", "valid-state"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider nicht gefunden");

        verifyNoInteractions(userRepository);
        verifyNoInteractions(payPalService);
    }

    @Test
    void refreshOnboardingStatus_persistsOnlyStatusReadFromPayPal() {
        User provider = provider("provider@example.com", "PROVIDER");
        provider.setPaypalMerchantId("existing-merchant");
        when(userRepository.findByEmail(provider.getEmail())).thenReturn(Optional.of(provider));
        when(payPalService.getSellerOnboardingStatusByMerchantId("existing-merchant"))
                .thenReturn(new PayPalService.PayPalSellerStatus(
                        "verified-merchant",
                        true,
                        "BUSINESS_ACCOUNT",
                        true,
                        true
                ));
        when(userRepository.save(provider)).thenReturn(provider);

        service.refreshOnboardingStatus(provider.getEmail());

        assertThat(provider.getPaypalMerchantId()).isEqualTo("verified-merchant");
        assertThat(provider.getPaypalPermissionsGranted()).isTrue();
        assertThat(provider.getPaypalEmailConfirmed()).isTrue();
        assertThat(provider.getPaypalOnboardingStatus()).isEqualTo("CONNECTED");
        verify(userRepository).save(provider);
    }

    @Test
    void refreshOnboardingStatus_rejectsNonProviderBeforeCallingPayPal() {
        User customer = provider("customer@example.com", "CUSTOMER");
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.refreshOnboardingStatus(customer.getEmail()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Diese Aktion ist nur fuer Provider erlaubt.");

        verifyNoInteractions(payPalService);
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private User provider(String email, String accountType) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setAccountType(accountType);
        user.setStatus("ACTIVE");
        return user;
    }
}
