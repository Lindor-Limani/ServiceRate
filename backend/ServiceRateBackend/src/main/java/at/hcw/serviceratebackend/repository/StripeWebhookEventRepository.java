package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.StripeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, UUID> {

    Optional<StripeWebhookEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
