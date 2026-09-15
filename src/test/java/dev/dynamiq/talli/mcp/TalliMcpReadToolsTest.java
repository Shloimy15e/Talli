package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.InvoiceItemRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.PaymentRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.ReportService;
import dev.dynamiq.talli.service.EmailThreadService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Test
    void emailListUsesDatabaseWindowAndConversationKeepsHtmlOnlyBodies() {
        ExpenseRepository expenses = mock(ExpenseRepository.class);
        EmailRepository emails = mock(EmailRepository.class);
        EmailThreadService threads = mock(EmailThreadService.class);
        TalliMcpReadTools tools = new TalliMcpReadTools(
                mock(ClientRepository.class), mock(ProjectRepository.class), mock(TimeEntryRepository.class),
                expenses, mock(InvoiceRepository.class), mock(InvoiceItemRepository.class),
                mock(PaymentRepository.class), mock(SubscriptionRepository.class), mock(ReportService.class),
                emails, threads);
        Email email = new Email();
        email.setId(8L);
        email.setSubject("Receipt");
        email.setBody("x".repeat(260));
        email.setBodyHtml("<p>Receipt</p>");
        email.setSource("mcp");
        email.setInitiatedBy("operator@example.test");
        email.setResendId("provider-123");
        email.setStatus("delivered");
        email.setErrorMessage("Initial provider delay");
        email.setBounceReason("Mailbox unavailable");
        email.setDeliveredAt(LocalDateTime.of(2026, 9, 15, 12, 30));
        when(emails.findClientEmails(7L, 51, 0)).thenReturn(List.of(email));
        when(emails.findById(8L)).thenReturn(java.util.Optional.of(email));
        when(threads.conversation(email)).thenReturn(List.of(email));

        var summary = tools.findClientEmails(7L, 0, 50);
        var conversation = tools.getEmailConversation(8L, 0, 50);

        assertThat(summary.emails().getFirst().body()).hasSize(250);
        assertThat(summary.emails().getFirst().bodyHtml()).isNull();
        assertThat(summary.emails().getFirst().source()).isEqualTo("mcp");
        assertThat(summary.emails().getFirst().initiatedBy()).isEqualTo("operator@example.test");
        assertThat(summary.emails().getFirst().providerId()).isEqualTo("provider-123");
        assertThat(summary.emails().getFirst().status()).isEqualTo("delivered");
        assertThat(summary.emails().getFirst().errorMessage()).isEqualTo("Initial provider delay");
        assertThat(summary.emails().getFirst().bounceReason()).isEqualTo("Mailbox unavailable");
        assertThat(conversation.messages().getFirst().bodyHtml()).isEqualTo("<p>Receipt</p>");
        assertThat(conversation.messages().getFirst().source()).isEqualTo("mcp");
        assertThat(conversation.messages().getFirst().initiatedBy()).isEqualTo("operator@example.test");
        assertThat(conversation.messages().getFirst().providerId()).isEqualTo("provider-123");
        assertThat(conversation.messages().getFirst().status()).isEqualTo("delivered");
        assertThat(conversation.messages().getFirst().errorMessage()).isEqualTo("Initial provider delay");
        assertThat(conversation.messages().getFirst().bounceReason()).isEqualTo("Mailbox unavailable");
        assertThat(conversation.messages().getFirst().deliveredAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 15, 12, 30));
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
                mock(ReportService.class),
                mock(EmailRepository.class),
                mock(EmailThreadService.class));
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
