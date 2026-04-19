package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

/**
 * All expense create/update paths go through here so the historic USD
 * exchange rate is captured on incurredOn uniformly — controllers, API, and
 * subscription-driven charges all benefit from the same rule.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExchangeRateService exchangeRateService;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExchangeRateService exchangeRateService) {
        this.expenseRepository = expenseRepository;
        this.exchangeRateService = exchangeRateService;
    }

    /** Lock the historic rate onto {@code e} based on its currency + incurredOn. */
    public void applyHistoricRate(Expense e) {
        e.setExchangeRate(exchangeRateService.lockedRate(e.getCurrency(), e.getIncurredOn()));
    }

    /** Persist a new expense, capturing the historic USD rate at incurred date. */
    @Transactional
    public Expense create(Expense e) {
        applyHistoricRate(e);
        return expenseRepository.save(e);
    }

    /**
     * Sum of all expense amounts in the window, converted to USD using each
     * expense's locked exchange rate. Use this instead of the repository's
     * raw SQL sum wherever the total might include non-USD rows.
     */
    public java.math.BigDecimal sumInUsdBetween(LocalDate from, LocalDate to) {
        return expenseRepository.findByIncurredOnBetweenOrderByIncurredOnDesc(from, to).stream()
                .filter(e -> e.getAmount() != null)
                .map(e -> exchangeRateService.toUsd(e.getAmount(), e.getCurrency(), e.getExchangeRate()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    /**
     * Persist edits to an existing expense. Only re-locks the rate when the
     * currency or the incurred date actually changed, so historical reports
     * for the original date stay reproducible.
     */
    @Transactional
    public Expense update(Expense e, String priorCurrency, LocalDate priorIncurredOn) {
        if (!Objects.equals(priorCurrency, e.getCurrency())
                || !Objects.equals(priorIncurredOn, e.getIncurredOn())) {
            applyHistoricRate(e);
        }
        return expenseRepository.save(e);
    }
}
