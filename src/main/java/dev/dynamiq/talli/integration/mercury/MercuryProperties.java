package dev.dynamiq.talli.integration.mercury;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MercuryProperties {

    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final String destinationAccountId;
    private final boolean sendInvoices;
    private final boolean achDebitEnabled;
    private final boolean creditCardEnabled;
    private final boolean useRealAccountNumber;
    private final String paymentPageBaseUrl;

    public MercuryProperties(
            @Value("${app.mercury.enabled:false}") boolean enabled,
            @Value("${app.mercury.api-key:}") String apiKey,
            @Value("${app.mercury.base-url:https://api.mercury.com/api/v1}") String baseUrl,
            @Value("${app.mercury.destination-account-id:}") String destinationAccountId,
            @Value("${app.mercury.send-invoices:false}") boolean sendInvoices,
            @Value("${app.mercury.ach-debit-enabled:true}") boolean achDebitEnabled,
            @Value("${app.mercury.credit-card-enabled:false}") boolean creditCardEnabled,
            @Value("${app.mercury.use-real-account-number:false}") boolean useRealAccountNumber,
            @Value("${app.mercury.payment-page-base-url:https://app.mercury.com/pay}") String paymentPageBaseUrl) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = withoutTrailingSlash(baseUrl);
        this.destinationAccountId = destinationAccountId == null ? "" : destinationAccountId.trim();
        this.sendInvoices = sendInvoices;
        this.achDebitEnabled = achDebitEnabled;
        this.creditCardEnabled = creditCardEnabled;
        this.useRealAccountNumber = useRealAccountNumber;
        this.paymentPageBaseUrl = withoutTrailingSlash(paymentPageBaseUrl);
    }

    public boolean isConfigured() {
        return enabled && !apiKey.isBlank() && !destinationAccountId.isBlank();
    }

    public String configurationError() {
        if (!enabled) return "Mercury integration is disabled.";
        if (apiKey.isBlank()) return "Mercury API key is not configured.";
        if (destinationAccountId.isBlank()) return "Mercury destination account is not configured.";
        return null;
    }

    public String paymentUrl(String slug, String status) {
        boolean payable = "Unpaid".equals(status) || "Processing".equals(status);
        return payable && slug != null && !slug.isBlank() ? paymentPageBaseUrl + "/" + slug : null;
    }

    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public String destinationAccountId() { return destinationAccountId; }
    public boolean sendInvoices() { return sendInvoices; }
    public boolean achDebitEnabled() { return achDebitEnabled; }
    public boolean creditCardEnabled() { return creditCardEnabled; }
    public boolean useRealAccountNumber() { return useRealAccountNumber; }

    private static String withoutTrailingSlash(String value) {
        if (value == null) return "";
        return value.replaceFirst("/+$", "");
    }
}
