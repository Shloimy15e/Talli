package dev.dynamiq.talli.integration.mercury;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class MercuryClient {

    private final MercuryProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MercuryClient(MercuryProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public Transaction getTransaction(String transactionId) {
        if (!properties.isApiConfigured()) {
            throw new IllegalStateException(properties.apiConfigurationError());
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + "/transaction/" + transactionId))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MercuryApiException(response.statusCode(), errorMessage(response.body()));
            }
            return objectMapper.readValue(response.body(), Transaction.class);
        } catch (MercuryApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mercury request was interrupted.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Mercury request failed: " + e.getMessage(), e);
        }
    }

    private String errorMessage(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            if (json.hasNonNull("message")) return json.get("message").asText();
            if (json.hasNonNull("error")) return json.get("error").asText();
        } catch (Exception ignored) {
        }
        return body == null || body.isBlank() ? "Mercury API request failed." : body;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(
            String id,
            BigDecimal amount,
            String counterpartyName,
            String kind,
            String status,
            String bankDescription,
            String externalMemo,
            String mercuryCategory,
            String note,
            String postedAt) {
    }
}
