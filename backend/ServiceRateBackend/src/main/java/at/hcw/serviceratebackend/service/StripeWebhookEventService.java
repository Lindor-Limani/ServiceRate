package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.StripeWebhookEvent;
import at.hcw.serviceratebackend.repository.StripeWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StripeWebhookEventService {

    static final int MAX_EVENT_ID_LENGTH = 255;
    static final int MAX_EVENT_TYPE_LENGTH = 255;

    private final StripeWebhookEventRepository stripeWebhookEventRepository;

    @Transactional
    public void processOnce(String eventId, String eventType, Runnable processing) {
        String validatedEventId = requireValue(eventId, "Event-ID", MAX_EVENT_ID_LENGTH);
        String validatedEventType = requireValue(eventType, "Event-Typ", MAX_EVENT_TYPE_LENGTH);
        Objects.requireNonNull(processing, "Webhook-Verarbeitung darf nicht fehlen.");

        StripeWebhookEvent inboxEvent = new StripeWebhookEvent();
        inboxEvent.setEventId(validatedEventId);
        inboxEvent.setEventType(validatedEventType);
        inboxEvent.setProcessedAt(OffsetDateTime.now());
        try {
            stripeWebhookEventRepository.saveAndFlush(inboxEvent);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateStripeWebhookEventException(validatedEventId, ex);
        }

        processing.run();
    }

    private String requireValue(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Stripe Event enthaelt keine gueltige " + field + ".");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("Stripe " + field + " ist zu lang.");
        }
        return normalized;
    }
}
