package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
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

    @InjectMocks
    private ProviderPayPalOnboardingService service;

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
