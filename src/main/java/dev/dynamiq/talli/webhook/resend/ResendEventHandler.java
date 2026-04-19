package dev.dynamiq.talli.webhook.resend;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Handles a single class of Resend webhook event.
 * Register a @Component implementing this interface — {@link ResendWebhookService}
 * auto-discovers all beans and dispatches each event to every handler that reports supports(type).
 *
 * <p>Event types (non-exhaustive): {@code email.sent}, {@code email.delivered},
 * {@code email.delivery_delayed}, {@code email.bounced}, {@code email.complained},
 * {@code email.opened}, {@code email.clicked}, {@code email.failed}.
 */
public interface ResendEventHandler {

    /** Return true to receive this event in {@link #handle(String, JsonNode)}. */
    boolean supports(String type);

    /**
     * @param type event type (e.g. {@code email.delivered})
     * @param data the {@code data} payload from the webhook body
     */
    void handle(String type, JsonNode data);
}
