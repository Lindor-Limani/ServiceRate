package at.hcw.serviceratebackend.service;

final class DuplicateStripeWebhookEventException extends RuntimeException {

    DuplicateStripeWebhookEventException(String eventId, Throwable cause) {
        super("Stripe Webhook Event wurde bereits verarbeitet: " + eventId, cause);
    }
}
