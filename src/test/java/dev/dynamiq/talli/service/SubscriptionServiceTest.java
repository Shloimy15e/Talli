package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {

    private SubscriptionRepository subscriptionRepository;
    private ExpenseRepository expenseRepository;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new SubscriptionService(subscriptionRepository, expenseRepository);
    }

    @Test
    void recordCharge_createsExpenseWithSubscriptionFields() {
        Subscription sub = monthlySub("GitHub", new BigDecimal("20.00"), "software", LocalDate.of(2026, 1, 15));
        sub.setPaymentMethod("Amex 1234");

        Expense created = service.recordCharge(sub, LocalDate.of(2026, 1, 15));

        assertThat(created.getAmount()).isEqualByComparingTo("20.00");
        assertThat(created.getVendor()).isEqualTo("GitHub");
        assertThat(created.getCategory()).isEqualTo("software");
        assertThat(created.getCurrency()).isEqualTo("USD");
        assertThat(created.getPaymentMethod()).isEqualTo("Amex 1234");
        assertThat(created.getSubscription()).isSameAs(sub);
        assertThat(created.getIncurredOn()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void recordCharge_advancesMonthlyNextDueByOneMonth() {
        Subscription sub = monthlySub("Figma", new BigDecimal("15.00"), "software", LocalDate.of(2026, 1, 15));

        service.recordCharge(sub, LocalDate.of(2026, 1, 15));

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getNextDueOn()).isEqualTo(LocalDate.of(2026, 2, 15));
    }

    @Test
    void recordCharge_advancesYearlyNextDueByOneYear() {
        Subscription sub = yearlySub("DomainReg", new BigDecimal("120.00"), "software", LocalDate.of(2026, 3, 1));

        service.recordCharge(sub, LocalDate.of(2026, 3, 1));

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getNextDueOn()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void recordCharge_savesBothExpenseAndSubscription() {
        Subscription sub = monthlySub("Slack", new BigDecimal("10.00"), "software", LocalDate.of(2026, 4, 1));

        service.recordCharge(sub, LocalDate.of(2026, 4, 1));

        verify(expenseRepository).save(any(Expense.class));
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void cancel_setsCancelledOnAndClearsNextDue() {
        Subscription sub = monthlySub("OldTool", new BigDecimal("5.00"), "software", LocalDate.of(2025, 6, 1));
        sub.setNextDueOn(LocalDate.of(2026, 5, 1));

        service.cancel(sub, LocalDate.of(2026, 4, 14));

        assertThat(sub.getCancelledOn()).isEqualTo(LocalDate.of(2026, 4, 14));
        assertThat(sub.getNextDueOn()).isNull();
        verify(subscriptionRepository).save(sub);
    }

    // --- helpers ---

    private Subscription monthlySub(String vendor, BigDecimal amount, String category, LocalDate startedOn) {
        return buildSub(vendor, amount, category, "monthly", startedOn);
    }

    private Subscription yearlySub(String vendor, BigDecimal amount, String category, LocalDate startedOn) {
        return buildSub(vendor, amount, category, "yearly", startedOn);
    }

    private Subscription buildSub(String vendor, BigDecimal amount, String category, String cycle, LocalDate startedOn) {
        Subscription s = new Subscription();
        s.setVendor(vendor);
        s.setAmount(amount);
        s.setCategory(category);
        s.setCycle(cycle);
        s.setCurrency("USD");
        s.setStartedOn(startedOn);
        s.setNextDueOn(startedOn);
        return s;
    }
}
