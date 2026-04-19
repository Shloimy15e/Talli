package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseServiceTest {

    private ExpenseRepository expenseRepository;
    private ExchangeRateService exchangeRateService;
    private ExpenseService service;

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        exchangeRateService = mock(ExchangeRateService.class);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        // Default lockedRate: 1.0 for USD, 3.5 for any other currency (test default).
        when(exchangeRateService.lockedRate(any(), any())).thenReturn(BigDecimal.ONE);
        service = new ExpenseService(expenseRepository, exchangeRateService);
    }

    @Test
    void create_locksHistoricRateForNonUsd() {
        when(exchangeRateService.lockedRate(eq("ILS"), eq(LocalDate.of(2026, 2, 1))))
                .thenReturn(new BigDecimal("3.70"));

        Expense e = expense("ILS", LocalDate.of(2026, 2, 1));
        Expense saved = service.create(e);

        assertThat(saved.getExchangeRate()).isEqualByComparingTo("3.70");
        verify(expenseRepository).save(e);
    }

    @Test
    void create_usesOneForUsd() {
        Expense e = expense("USD", LocalDate.of(2026, 2, 1));
        Expense saved = service.create(e);

        assertThat(saved.getExchangeRate()).isEqualByComparingTo("1");
    }

    @Test
    void update_preservesRateWhenCurrencyAndDateUnchanged() {
        Expense e = expense("ILS", LocalDate.of(2026, 1, 1));
        e.setExchangeRate(new BigDecimal("3.50"));

        // Same currency and date as prior → no re-lock.
        service.update(e, "ILS", LocalDate.of(2026, 1, 1));

        assertThat(e.getExchangeRate()).isEqualByComparingTo("3.50");
        verify(exchangeRateService, never()).lockedRate(any(), any());
    }

    @Test
    void update_reLocksWhenCurrencyChanges() {
        when(exchangeRateService.lockedRate(eq("EUR"), eq(LocalDate.of(2026, 1, 1))))
                .thenReturn(new BigDecimal("0.92"));

        Expense e = expense("EUR", LocalDate.of(2026, 1, 1));
        e.setExchangeRate(new BigDecimal("3.50")); // stale

        service.update(e, "ILS", LocalDate.of(2026, 1, 1));

        assertThat(e.getExchangeRate()).isEqualByComparingTo("0.92");
    }

    @Test
    void update_reLocksWhenDateChanges() {
        when(exchangeRateService.lockedRate(eq("ILS"), eq(LocalDate.of(2025, 6, 1))))
                .thenReturn(new BigDecimal("3.60"));

        Expense e = expense("ILS", LocalDate.of(2025, 6, 1));
        e.setExchangeRate(new BigDecimal("3.50")); // rate from prior date

        service.update(e, "ILS", LocalDate.of(2026, 1, 1));

        assertThat(e.getExchangeRate()).isEqualByComparingTo("3.60");
    }

    @Test
    void applyHistoricRate_usesCurrencyAndDateOnEntity() {
        when(exchangeRateService.lockedRate(eq("GBP"), eq(LocalDate.of(2026, 3, 15))))
                .thenReturn(new BigDecimal("0.79"));

        Expense e = expense("GBP", LocalDate.of(2026, 3, 15));
        service.applyHistoricRate(e);

        assertThat(e.getExchangeRate()).isEqualByComparingTo("0.79");
    }

    @Test
    void sumInUsdBetween_convertsEachExpenseWithItsLockedRate() {
        Expense usd = expense("USD", LocalDate.of(2026, 4, 1));
        usd.setAmount(new BigDecimal("50.00"));
        usd.setExchangeRate(BigDecimal.ONE);
        Expense ils = expense("ILS", LocalDate.of(2026, 4, 2));
        ils.setAmount(new BigDecimal("350.00"));
        ils.setExchangeRate(new BigDecimal("3.50")); // 350 ILS / 3.5 = 100 USD

        when(expenseRepository.findByIncurredOnBetweenOrderByIncurredOnDesc(
                any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(java.util.List.of(usd, ils));
        when(exchangeRateService.toUsd(eq(new BigDecimal("50.00")), eq("USD"), any()))
                .thenReturn(new BigDecimal("50.00"));
        when(exchangeRateService.toUsd(eq(new BigDecimal("350.00")), eq("ILS"), eq(new BigDecimal("3.50"))))
                .thenReturn(new BigDecimal("100.00"));

        BigDecimal total = service.sumInUsdBetween(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(total).isEqualByComparingTo("150.00");
    }

    // --- helpers ---

    private Expense expense(String currency, LocalDate incurredOn) {
        Expense e = new Expense();
        e.setCurrency(currency);
        e.setIncurredOn(incurredOn);
        e.setAmount(new BigDecimal("100.00"));
        e.setCategory("software");
        return e;
    }
}
