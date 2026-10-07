package dev.dynamiq.talli.webhook.resend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.webhook.svix.SvixSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Verifies Resend webhook signatures and dispatches events to every registered
 * {@link ResendEventHandler}. Adding new behaviour means dropping in a new handler bean —
 * no edits here.
 */
@Service
public class ResendWebhookService {

    private static final Logger log = LoggerFactory.getLogger(ResendWebhookService.class);

    private final List<ResendEventHandler> handlers;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.mail.resend.webhook-secret:}")
    private String webhookSecret;

    public ResendWebhookService(List<ResendEventHandler> handlers) {
        this.handlers = handlers;
    }

    public static class InvalidSignatureException extends RuntimeException {
        public InvalidSignatureException(String msg) { super(msg); }
    }

    /**
     * @throws InvalidSignatureException if signature verification fails — controller should return 401.
     * @throws RuntimeException          on malformed JSON — controller should return 400.
     */
    public void process(String body, String id, String timestamp, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("Resend webhook secret is not configured.");
        }
        if (!SvixSignatureVerifier.verify(webhookSecret, id, timestamp, signature, body)) {
            throw new InvalidSignatureException("Webhook signature did not verify.");
        }

        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Invalid webhook JSON: " + e.getMessage(), e);
        }

        String type = root.path("type").asText(null);
        JsonNode data = root.path("data");
        if (type == null || data.isMissingNode()) {
            throw new RuntimeException("Webhook body missing 'type' or 'data'.");
        }

        RetryableResendEventException retryableFailure = null;
        for (ResendEventHandler handler : handlers) {
            if (!handler.supports(type)) continue;
            try {
                handler.handle(type, data);
            } catch (RetryableResendEventException e) {
                retryableFailure = e;
                log.error("Durable inbound handling failed for event {}", type, e);
            } catch (Exception e) {
                if (handler.retryOnFailure()) {
                    retryableFailure = new RetryableResendEventException("Inbound transaction failed", e);
                }
                // Continue sibling handlers; durable failures request a provider retry
                // after dispatch, while other handler failures remain isolated.
                log.error("Handler {} threw on event {}: {}",
                        handler.getClass().getSimpleName(), type, e.getMessage(), e);
            }
        }
        if (retryableFailure != null) throw retryableFailure;
    }
}
