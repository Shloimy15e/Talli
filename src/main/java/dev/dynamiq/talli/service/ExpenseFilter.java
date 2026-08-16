package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Expense;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/** Normalized filters shared by the expense controller, service, and view. */
public record ExpenseFilter(
        String search,
        Long clientId,
        Long projectId,
        String category,
        String source,
        String billing,
        LocalDate from,
        LocalDate to) {

    private static final Set<String> SOURCES = Set.of("manual", "mercury", "subscription");
    private static final Set<String> BILLING_STATES = Set.of("unbilled", "billed", "nonbillable");
    private static final Set<String> CATEGORIES = Set.copyOf(Expense.CATEGORIES);

    public ExpenseFilter {
        search = cleanText(search);
        clientId = positiveId(clientId);
        projectId = positiveId(projectId);
        category = allowedCode(category, CATEGORIES);
        source = allowedCode(source, SOURCES);
        billing = allowedCode(billing, BILLING_STATES);
    }

    public boolean active() {
        return !search.isEmpty() || clientId != null || projectId != null
                || !category.isEmpty() || !source.isEmpty() || !billing.isEmpty()
                || from != null || to != null;
    }

    public boolean hasInvalidDateRange() {
        return from != null && to != null && from.isAfter(to);
    }

    private static String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private static Long positiveId(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private static String allowedCode(String value, Set<String> allowed) {
        String code = cleanText(value).toLowerCase(Locale.ROOT);
        return allowed.contains(code) ? code : "";
    }
}
