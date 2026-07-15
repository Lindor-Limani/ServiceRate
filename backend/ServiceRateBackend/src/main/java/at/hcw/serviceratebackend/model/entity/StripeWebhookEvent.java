package at.hcw.serviceratebackend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "stripe_webhook_events", uniqueConstraints =
        @UniqueConstraint(name = "ux_stripe_webhook_events_event_id", columnNames = "event_id"))
public class StripeWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 255)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;
}
