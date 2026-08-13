package dev.dynamiq.talli.integration.mercury;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class MercuryClient {

    private final MercuryProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MercuryClient(MercuryProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Customer createCustomer(String name, String email) {
        return request("POST", "/ar/customers", new CreateCustomerRequest(name, email), Customer.class);
    }

    public Invoice createInvoice(CreateInvoiceRequest body) {
        return request("POST", "/ar/invoices", body, Invoice.class);
    }

    public Invoice getInvoice(String invoiceId) {
        return request("GET", "/ar/invoices/" + invoiceId, null, Invoice.class);
    }

    private <T> T request(String method, String path, Object body, Class<T> responseType) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(properties.configurationError());
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey());

            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MercuryApiException(response.statusCode(), errorMessage(response.body()));
            }
            return objectMapper.readValue(response.body(), responseType);
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

    public record Customer(String id, String name, String email) {}

    public record CreateCustomerRequest(String name, String email) {}

    public record LineItem(String name, BigDecimal quantity, BigDecimal unitPrice) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateInvoiceRequest(
            boolean achDebitEnabled,
            List<String> ccEmails,
            boolean creditCardEnabled,
            String currencyCode,
            String customerId,
            String destinationAccountId,
            String dueDate,
            String internalNote,
            String invoiceDate,
            String invoiceNumber,
            List<LineItem> lineItems,
            String payerMemo,
            String sendEmailOption,
            String servicePeriodEndDate,
            String servicePeriodStartDate,
            boolean useRealAccountNumber) {}

    public record Invoice(
            String id,
            BigDecimal amount,
            String currencyCode,
            String customerId,
            String invoiceNumber,
            String slug,
            String status,
            String updatedAt) {}
}
