package dev.dynamiq.talli.integration.mercury;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MercuryProperties {

    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final String webhookSecret;

    public MercuryProperties(
            @Value("${app.mercury.enabled:false}") boolean enabled,
            @Value("${app.mercury.api-key:}") String apiKey,
            @Value("${app.mercury.base-url:https://api.mercury.com/api/v1}") String baseUrl,
            @Value("${app.mercury.webhook-secret:}") String webhookSecret) {
        this.enabled = enabled;
        this.apiKey = clean(apiKey);
        this.baseUrl = clean(baseUrl).replaceFirst("/+$", "");
        this.webhookSecret = clean(webhookSecret);
    }

    public boolean isExpenseSyncConfigured() {
        return isApiConfigured() && !webhookSecret.isBlank();
    }

    public boolean isApiConfigured() {
        return enabled && !apiKey.isBlank();
    }

    public String configurationError() {
        if (!enabled) return "Mercury expense sync is disabled.";
        if (apiKey.isBlank()) return "Mercury API key is not configured.";
        if (webhookSecret.isBlank()) return "Mercury webhook secret is not configured.";
        return null;
    }

    public String apiConfigurationError() {
        if (!enabled) return "Mercury expense sync is disabled.";
        if (apiKey.isBlank()) return "Mercury API key is not configured.";
        return null;
    }

    public boolean enabled() { return enabled; }
    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public String webhookSecret() { return webhookSecret; }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
