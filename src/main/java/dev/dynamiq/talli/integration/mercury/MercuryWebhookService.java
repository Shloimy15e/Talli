package dev.dynamiq.talli.integration.mercury;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class MercuryWebhookService {

    private final MercuryProperties properties;
    private final ObjectMapper objectMapper;
    private final MercuryExpenseSyncService expenseSyncService;

    public MercuryWebhookService(MercuryProperties properties,
            ObjectMapper objectMapper,
            MercuryExpenseSyncService expenseSyncService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.expenseSyncService = expenseSyncService;
    }

    public MercuryExpenseSyncService.ImportResult process(String body, String signature) {
        if (!properties.isExpenseSyncConfigured()) {
            throw new IllegalStateException(properties.configurationError());
        }
        if (!MercurySignatureVerifier.verify(properties.webhookSecret(), signature, body)) {
            throw new InvalidSignatureException();
        }

        JsonNode event;
        try {
            event = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BadPayloadException("Invalid Mercury webhook payload.", e);
        }

        if (!"transaction".equals(event.path("resourceType").asText())) {
            return MercuryExpenseSyncService.ImportResult.ignored();
        }

        String transactionId = event.path("resourceId").asText();
        if (transactionId.isBlank()) throw new BadPayloadException("Missing transaction resource ID.");
        return expenseSyncService.importTransaction(transactionId);
    }

    public static class InvalidSignatureException extends RuntimeException {
    }

    public static class BadPayloadException extends RuntimeException {
        public BadPayloadException(String message) {
            super(message);
        }

        public BadPayloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
