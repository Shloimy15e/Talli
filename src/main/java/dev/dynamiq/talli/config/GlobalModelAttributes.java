package dev.dynamiq.talli.config;

import dev.dynamiq.talli.model.Expense;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Map;

/**
 * Makes utility data available to every Thymeleaf template without
 * each controller having to add it manually.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private static final Map<String, String> CURRENCY_SYMBOLS = Map.of(
            "USD", "$",
            "ILS", "\u20AA",   // ₪
            "EUR", "\u20AC",   // €
            "GBP", "\u00A3"    // £
    );

    /**
     * Returns a symbol for a currency code, falling back to the code itself
     * if no symbol is mapped. Usage in templates:
     *   th:text="${csym.apply(invoice.currency)} + ${amount}"
     *   → "$ 1,500.00" or "₪ 1,500.00"
     */
    @ModelAttribute("csym")
    public java.util.function.Function<String, String> currencySymbolFn() {
        return code -> CURRENCY_SYMBOLS.getOrDefault(code, code + " ");
    }

    @ModelAttribute("expenseCategories")
    public List<String> expenseCategories() {
        return Expense.CATEGORIES;
    }
}
