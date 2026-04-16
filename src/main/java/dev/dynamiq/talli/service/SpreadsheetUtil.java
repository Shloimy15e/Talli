package dev.dynamiq.talli.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public final class SpreadsheetUtil {

    private SpreadsheetUtil() {}

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("d/MMM/yy"),
            DateTimeFormatter.ofPattern("dd/MMM/yy"),
            DateTimeFormatter.ofPattern("MMMM d, yyyy"),
            DateTimeFormatter.ofPattern("MMM d, yyyy")
    );

    public static String val(Map<String, String> row, String key) {
        String v = row.get(key);
        return v != null ? v.trim() : "";
    }

    public static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();

        // Excel serial number (e.g. 45697)
        if (s.matches("\\d{5,}")) {
            try {
                long serial = Long.parseLong(s);
                return LocalDate.ofEpochDay(serial - 25569);
            } catch (Exception ignored) {}
        }

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    public static BigDecimal parseBigDecimal(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        String cleaned = s.trim().replaceAll("[^\\d.\\-]", "");
        if (cleaned.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static int parseMinutes(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String currencyFromSymbol(String symbol) {
        if (symbol == null) return "USD";
        return switch (symbol.trim()) {
            case "\u20AA", "?" -> "ILS";
            case "$" -> "USD";
            case "\u20AC" -> "EUR";
            case "\u00A3" -> "GBP";
            default -> "USD";
        };
    }
}
