package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.repository.StripeWebhookEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StripeWebhookEventServiceIntegrationTest {

    private static final int PARALLEL_REQUESTS = 10;

    @Autowired
    private StripeWebhookEventService stripeWebhookEventService;

    @Autowired
    private StripeWebhookEventRepository stripeWebhookEventRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        stripeWebhookEventRepository.deleteAll();
        executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void successfulProcessingPersistsEventClaim() {
        AtomicInteger effects = new AtomicInteger();

        stripeWebhookEventService.processOnce(
                "evt_success", "checkout.session.completed", effects::incrementAndGet
        );

        assertThat(effects).hasValue(1);
        assertThat(stripeWebhookEventRepository.findByEventId("evt_success"))
                .hasValueSatisfying(event -> {
                    assertThat(event.getEventType()).isEqualTo("checkout.session.completed");
                    assertThat(event.getProcessedAt()).isNotNull();
                });
    }

    @Test
    void invalidEventIdIsRejectedBeforeProcessing() {
        AtomicInteger effects = new AtomicInteger();

        assertThatThrownBy(() -> stripeWebhookEventService.processOnce(
                "  ", "checkout.session.completed", effects::incrementAndGet
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stripe Event enthaelt keine gueltige Event-ID.");

        assertThat(effects).hasValue(0);
        assertThat(stripeWebhookEventRepository.count()).isZero();
    }

    @Test
    void failedProcessingRollsBackClaimAndAllowsRetry() {
        assertThatThrownBy(() -> stripeWebhookEventService.processOnce(
                "evt_retry", "checkout.session.completed", () -> {
                    throw new IllegalArgumentException("Buchung fehlt");
                }
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Buchung fehlt");
        assertThat(stripeWebhookEventRepository.existsByEventId("evt_retry")).isFalse();

        AtomicInteger retryEffects = new AtomicInteger();
        stripeWebhookEventService.processOnce(
                "evt_retry", "checkout.session.completed", retryEffects::incrementAndGet
        );

        assertThat(retryEffects).hasValue(1);
        assertThat(stripeWebhookEventRepository.existsByEventId("evt_retry")).isTrue();
    }

    @Test
    void parallelReplaysProduceExactlyOneCommittedEffect() throws Exception {
        CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger effects = new AtomicInteger();
        List<Future<Boolean>> results = new ArrayList<>();

        for (int request = 0; request < PARALLEL_REQUESTS; request++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Paralleler Teststart fehlgeschlagen");
                }
                try {
                    stripeWebhookEventService.processOnce(
                            "evt_parallel", "checkout.session.completed", effects::incrementAndGet
                    );
                    return true;
                } catch (DuplicateStripeWebhookEventException ex) {
                    return false;
                }
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successfulRequests = 0;
        for (Future<Boolean> result : results) {
            if (result.get(20, TimeUnit.SECONDS)) {
                successfulRequests++;
            }
        }

        assertThat(successfulRequests).isEqualTo(1);
        assertThat(effects).hasValue(1);
        assertThat(stripeWebhookEventRepository.findAll())
                .singleElement()
                .extracting(event -> event.getEventId())
                .isEqualTo("evt_parallel");
    }
}
