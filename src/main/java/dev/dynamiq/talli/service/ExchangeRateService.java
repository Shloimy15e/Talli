package dev.dynamiq.talli.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches exchange rates with a 1-hour cache.
 * Rates are "1 USD = X foreign", so to convert foreign → USD: amount / rate.
 * USD always returns 1.0.
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String API_URL = "https://open.er-api.com/v6/latest/USD";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Map<String, BigDecimal> cachedRates = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> historicCache = new ConcurrentHashMap<>();
    private Instant cacheExpiry = Instant.MIN;

    public ExchangeRateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Get rate for currency vs USD. e.g., "ILS" → 3.5 means 1 USD = 3.5 ILS. */
    public BigDecimal getRate(String currency) {
        if (currency == null || "USD".equalsIgnoreCase(currency)) return BigDecimal.ONE;

        refreshIfStale();

        BigDecimal rate = cachedRates.get(currency.toUpperCase());
        if (rate != null) return rate;

        log.warn("No rate found for currency: {}, defaulting to 1.0", currency);
        return BigDecimal.ONE;
    }

    /** Convert an amount from the given currency to USD. */
    public BigDecimal toUsd(BigDecimal amount, String currency, BigDecimal exchangeRate) {
        if (amount == null || amount.signum() == 0) return BigDecimal.ZERO;
        if ("USD".equalsIgnoreCase(currency)) return amount;
        if (exchangeRate != null && exchangeRate.signum() > 0) {
            return amount.divide(exchangeRate, 2, java.math.RoundingMode.HALF_UP);
        }
        return amount;
    }

    /** Convert an amount from the given currency to USD using current rate. */
    public BigDecimal toUsdCurrent(BigDecimal amount, String currency) {
        return toUsd(amount, currency, getRate(currency));
    }

    /**
     * The rate to lock onto a row incurred on {@code date} in {@code currency}.
     * USD (or null inputs) → 1.0; otherwise the historic rate for that date.
     * Safe to call from create/update paths regardless of currency.
     */
    public BigDecimal lockedRate(String currency, LocalDate date) {
        if (date == null || currency == null || "USD".equalsIgnoreCase(currency)) return BigDecimal.ONE;
        return getHistoricRate(currency, date);
    }

    /** Get the historic rate for a specific date. Uses fawazahmed0/currency-api via jsDelivr CDN. */
    public BigDecimal getHistoricRate(String currency, LocalDate date) {
        if (currency == null || "USD".equalsIgnoreCase(currency)) return BigDecimal.ONE;

        // Clamp future dates to today
        LocalDate queryDate = date.isAfter(LocalDate.now()) ? LocalDate.now() : date;

        String cacheKey = queryDate + ":" + currency.toLowerCase();
        BigDecimal cached = historicCache.get(cacheKey);
        if (cached != null) return cached;

        try {
            String url = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@"
                    + queryDate + "/v1/currencies/usd.json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode rates = root.get("usd");
            if (rates != null && rates.has(currency.toLowerCase())) {
                BigDecimal rate = rates.get(currency.toLowerCase()).decimalValue();
                historicCache.put(cacheKey, rate);
                return rate;
            }
        } catch (Exception e) {
            log.error("Failed to fetch historic rate for {} on {}: {}", currency, queryDate, e.getMessage());
        }

        log.warn("No historic rate for {} on {}, falling back to current", currency, queryDate);
        return getRate(currency);
    }

    private void refreshIfStale() {
        if (Instant.now().isBefore(cacheExpiry) && !cachedRates.isEmpty()) return;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode rates = root.get("rates");

            Map<String, BigDecimal> newRates = new ConcurrentHashMap<>();
            rates.fields().forEachRemaining(entry ->
                    newRates.put(entry.getKey(), entry.getValue().decimalValue()));

            cachedRates = newRates;
            cacheExpiry = Instant.now().plus(CACHE_TTL);
            log.info("Exchange rates refreshed, {} currencies cached", newRates.size());
        } catch (Exception e) {
            log.error("Failed to fetch exchange rates: {}", e.getMessage());
            // Keep stale cache if available, extend expiry to avoid hammering
            if (!cachedRates.isEmpty()) {
                cacheExpiry = Instant.now().plus(Duration.ofMinutes(5));
            }
        }
    }
}
