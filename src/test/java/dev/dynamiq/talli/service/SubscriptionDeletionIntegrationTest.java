package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@RefreshDatabaseTest
class SubscriptionDeletionIntegrationTest {

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private ExpenseRepository expenses;

    @Autowired
    private SubscriptionService subscriptionService;

    @Test
    void deletePreservesAndUnlinksHistoricalExpenses() {
        Subscription subscription = new Subscription();
        subscription.setVendor("GitHub");
        subscription.setCategory("software");
        subscription.setAmount(new BigDecimal("20.00"));
        subscription.setCurrency("USD");
        subscription.setCycle("monthly");
        subscription.setStartedOn(LocalDate.of(2026, 8, 1));
        subscription.setNextDueOn(LocalDate.of(2026, 9, 1));
        subscription = subscriptions.saveAndFlush(subscription);

        Expense expense = new Expense();
        expense.setSubscription(subscription);
        expense.setIncurredOn(LocalDate.of(2026, 8, 1));
        expense.setAmount(new BigDecimal("20.00"));
        expense.setCurrency("USD");
        expense.setExchangeRate(BigDecimal.ONE);
        expense.setCategory("software");
        expense.setBillable(false);
        expense.setBilled(false);
        expense = expenses.saveAndFlush(expense);

        int unlinked = subscriptionService.delete(subscription);

        assertThat(unlinked).isEqualTo(1);
        assertThat(subscriptions.findById(subscription.getId())).isEmpty();
        assertThat(expenses.findById(expense.getId())).get()
                .extracting(Expense::getSubscription)
                .isNull();
    }
}
