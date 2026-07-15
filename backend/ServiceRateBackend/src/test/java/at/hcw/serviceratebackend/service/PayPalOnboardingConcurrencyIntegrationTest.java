package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PayPalOnboardingConcurrencyIntegrationTest {

    @Autowired
    private ProviderPayPalOnboardingService onboardingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PayPalOnboardingStateService onboardingStateService;

    @MockitoBean
    private PayPalService payPalService;

    private User provider;

    @BeforeEach
    void setUp() {
        provider = new User();
        provider.setId(UUID.randomUUID());
        provider.setEmail("paypal-concurrency-" + provider.getId() + "@example.com");
        provider.setPasswordHash("not-used");
        provider.setFirstName("Parallel");
        provider.setLastName("Provider");
        provider.setAccountType("PROVIDER");
        provider.setStatus("ACTIVE");
        provider.setEmailVerified(true);
        userRepository.saveAndFlush(provider);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(provider.getId());
    }

    @Test
    void sameStateConsumedConcurrently_allowsExactlyOneConsumer() throws Exception {
        createOnboardingState(provider);
        String storedHash = userRepository.findById(provider.getId()).orElseThrow().getPaypalOnboardingStateHash();

        int requestCount = 10;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        onboardingStateService.consume(provider.getEmail(), storedHash, OffsetDateTime.now());
                        return "SUCCESS";
                    } catch (IllegalArgumentException expectedReplayRejection) {
                        return expectedReplayRejection.getMessage();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(20, TimeUnit.SECONDS));
            }
            long successes = outcomes.stream().filter("SUCCESS"::equals).count();
            assertThat(successes).as("Parallel outcomes: %s", outcomes).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        User persisted = userRepository.findById(provider.getId()).orElseThrow();
        assertThat(persisted.getPaypalOnboardingStateHash()).isNull();
        assertThat(persisted.getPaypalOnboardingStateExpiresAt()).isNull();
        verify(payPalService, never()).getSellerOnboardingStatusByTrackingId(any(String.class));
    }

    @Test
    void validStateCompletedOnce_readsPayPalStatusAndConsumesState() {
        String generatedState = createOnboardingState(provider);
        when(payPalService.getSellerOnboardingStatusByTrackingId(provider.getId().toString()))
                .thenReturn(new PayPalService.PayPalSellerStatus(
                        "verified-merchant", true, "BUSINESS_ACCOUNT", true, true
                ));

        onboardingService.completeOnboarding(provider.getEmail(), generatedState);

        verify(payPalService).getSellerOnboardingStatusByTrackingId(provider.getId().toString());
        User persisted = userRepository.findById(provider.getId()).orElseThrow();
        assertThat(persisted.getPaypalOnboardingStateHash()).isNull();
        assertThat(persisted.getPaypalMerchantId()).isEqualTo("verified-merchant");
    }

    @Test
    void expiredState_isRejectedWithoutPayPalStatusReadOrReceiverMutation() {
        String generatedState = createOnboardingState(provider);
        User expired = userRepository.findById(provider.getId()).orElseThrow();
        expired.setPaypalOnboardingStateExpiresAt(OffsetDateTime.now().minusSeconds(1));
        userRepository.saveAndFlush(expired);

        assertThatThrownBy(() -> onboardingService.completeOnboarding(provider.getEmail(), generatedState))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PayPal-Onboarding-State ist ungültig oder abgelaufen.");

        verify(payPalService, never()).getSellerOnboardingStatusByTrackingId(any(String.class));
        User unchanged = userRepository.findById(provider.getId()).orElseThrow();
        assertThat(unchanged.getPaypalMerchantId()).isNull();
        assertThat(unchanged.getPaypalOnboardingStatus()).isEqualTo("LINK_CREATED");
    }

    @Test
    void stateBoundToAnotherProvider_isRejectedWithoutPayPalStatusRead() {
        String generatedState = createOnboardingState(provider);
        User otherProvider = new User();
        otherProvider.setId(UUID.randomUUID());
        otherProvider.setEmail("other-paypal-" + otherProvider.getId() + "@example.com");
        otherProvider.setPasswordHash("not-used");
        otherProvider.setAccountType("PROVIDER");
        otherProvider.setStatus("ACTIVE");
        userRepository.saveAndFlush(otherProvider);
        try {
            assertThatThrownBy(() -> onboardingService.completeOnboarding(otherProvider.getEmail(), generatedState))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PayPal-Onboarding-State ist ungültig oder abgelaufen.");

            verify(payPalService, never()).getSellerOnboardingStatusByTrackingId(any(String.class));
            assertThat(userRepository.findById(provider.getId()).orElseThrow().getPaypalOnboardingStateHash()).isNotNull();
        } finally {
            userRepository.deleteById(otherProvider.getId());
        }
    }

    private String createOnboardingState(User targetProvider) {
        AtomicReference<String> generatedState = new AtomicReference<>();
        when(payPalService.createSellerOnboardingLink(any(User.class), any(String.class)))
                .thenAnswer(invocation -> {
                    generatedState.set(invocation.getArgument(1));
                    return new PayPalService.PayPalReferral("https://paypal.example/action", "https://paypal.example/self");
                });
        onboardingService.createOnboardingLink(targetProvider.getEmail());
        assertThat(generatedState.get()).isNotBlank();
        String expectedHash;
        try {
            expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(generatedState.get().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        User persisted = userRepository.findById(targetProvider.getId()).orElseThrow();
        assertThat(persisted.getPaypalOnboardingStateHash()).isEqualTo(expectedHash);
        assertThat(persisted.getPaypalOnboardingStateExpiresAt()).isAfter(OffsetDateTime.now());
        return generatedState.get();
    }
}
