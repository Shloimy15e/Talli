package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.InvoiceItemRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.PaymentRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.ReportService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TalliMcpReadToolsTest {

    @Test
    void findExpensesFiltersBySubscriptionAndLinkedState() {
        ExpenseRepository expenses = mock(ExpenseRepository.class);
        Subscription subscription = new Subscription();
        subscription.setId(7L);
        subscription.setVendor("GitHub");
        Expense linked = expense(1L);
        linked.setSubscription(subscription);
        Expense unlinked = expense(2L);
        when(expenses.findAllByOrderByIncurredOnDesc()).thenReturn(List.of(linked, unlinked));
        TalliMcpReadTools tools = tools(expenses);

        var bySubscription = tools.findExpenses(null, null, 7L, null,
                null, null, null, null, null, null, null);
        var withoutSubscription = tools.findExpenses(null, null, null, false,
                null, null, null, null, null, null, null);

        assertThat(bySubscription).extracting(McpViews.ExpenseView::id).containsExactly(1L);
        assertThat(bySubscription.getFirst().subscriptionVendor()).isEqualTo("GitHub");
        assertThat(withoutSubscription).extracting(McpViews.ExpenseView::id).containsExactly(2L);
    }

    private static TalliMcpReadTools tools(ExpenseRepository expenses) {
        return new TalliMcpReadTools(
                mock(ClientRepository.class),
                mock(ProjectRepository.class),
                mock(TimeEntryRepository.class),
                expenses,
                mock(InvoiceRepository.class),
                mock(InvoiceItemRepository.class),
                mock(PaymentRepository.class),
                mock(SubscriptionRepository.class),
                mock(ReportService.class));
    }

    private static Expense expense(Long id) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setIncurredOn(LocalDate.of(2026, 8, 1));
        expense.setAmount(new BigDecimal("20.00"));
        expense.setCurrency("USD");
        expense.setExchangeRate(BigDecimal.ONE);
        expense.setCategory("software");
        expense.setBillable(false);
        expense.setBilled(false);
        return expense;
    }
}
