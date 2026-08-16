package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import dev.dynamiq.talli.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TalliMcpSubscriptionToolsTest {

    private SubscriptionRepository subscriptions;
    private ClientRepository clients;
    private ProjectRepository projects;
    private ExpenseRepository expenses;
    private SubscriptionService subscriptionService;
    private TalliMcpSubscriptionTools tools;

    @BeforeEach
    void setUp() {
        subscriptions = mock(SubscriptionRepository.class);
        clients = mock(ClientRepository.class);
        projects = mock(ProjectRepository.class);
        expenses = mock(ExpenseRepository.class);
        subscriptionService = mock(SubscriptionService.class);
        when(subscriptions.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            if (subscription.getId() == null) subscription.setId(7L);
            return subscription;
        });
        tools = new TalliMcpSubscriptionTools(subscriptions, clients, projects,
                expenses, subscriptionService);
    }

    @Test
    void createSubscriptionUsesProjectClientAndDefaultsNextDueDate() {
        Client client = client(1L, "Acme");
        Project project = project(2L, "Website", client);
        when(projects.findById(2L)).thenReturn(Optional.of(project));

        var result = tools.createSubscription("GitHub", "software", new BigDecimal("20.00"),
                "monthly", "2026-08-01", "usd", null, 2L, "Code hosting",
                null, "https://example.test/manage", "https://example.test/cancel", "card");

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.clientId()).isEqualTo(1L);
        assertThat(result.projectId()).isEqualTo(2L);
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.nextDueOn()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.manageUrl()).isEqualTo("https://example.test/manage");
    }

    @Test
    void createSubscriptionRejectsMismatchedClientAndProject() {
        Client projectClient = client(1L, "Acme");
        when(projects.findById(2L)).thenReturn(Optional.of(project(2L, "Website", projectClient)));
        when(clients.findById(9L)).thenReturn(Optional.of(client(9L, "Other")));

        assertThatThrownBy(() -> tools.createSubscription("GitHub", "software",
                new BigDecimal("20.00"), "monthly", "2026-08-01", "USD",
                9L, 2L, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("project_id does not belong to client_id");
    }

    @Test
    void updateSubscriptionPatchesFieldsAndCanClearProject() {
        Client client = client(1L, "Acme");
        Subscription subscription = subscription(7L, "GitHub", client,
                project(2L, "Website", client));
        when(subscriptions.findById(7L)).thenReturn(Optional.of(subscription));

        var result = tools.updateSubscription(7L, null, null, new BigDecimal("25.00"),
                null, null, null, null, 0L, " ", null, null, null, null);

        assertThat(result.amount()).isEqualByComparingTo("25.00");
        assertThat(result.projectId()).isNull();
        assertThat(result.clientId()).isEqualTo(1L);
        assertThat(result.description()).isNull();
    }

    @Test
    void updateSubscriptionRequiresAtLeastOneField() {
        assertThatThrownBy(() -> tools.updateSubscription(7L, null, null, null,
                null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one subscription field is required");
    }

    @Test
    void deleteSubscriptionRequiresConfirmationAndReportsUnlinkedExpenses() {
        Subscription subscription = subscription(7L, "GitHub", null, null);
        when(subscriptions.findById(7L)).thenReturn(Optional.of(subscription));
        when(subscriptionService.delete(subscription)).thenReturn(4);

        assertThatThrownBy(() -> tools.deleteSubscription(7L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirmed must be true");
        var result = tools.deleteSubscription(7L, true);

        assertThat(result.subscriptionId()).isEqualTo(7L);
        assertThat(result.unlinkedExpenseCount()).isEqualTo(4);
        assertThat(result.deleted()).isTrue();
    }

    @Test
    void cancelAndReactivateUseLifecycleService() {
        Subscription subscription = subscription(7L, "GitHub", null, null);
        when(subscriptions.findById(7L)).thenReturn(Optional.of(subscription));
        when(subscriptionService.cancel(subscription, LocalDate.of(2026, 8, 10)))
                .thenAnswer(invocation -> {
                    subscription.setCancelledOn(invocation.getArgument(1));
                    subscription.setNextDueOn(null);
                    return subscription;
                });
        when(subscriptionService.reactivate(subscription, LocalDate.of(2026, 9, 1)))
                .thenAnswer(invocation -> {
                    subscription.setCancelledOn(null);
                    subscription.setNextDueOn(invocation.getArgument(1));
                    return subscription;
                });

        var cancelled = tools.cancelSubscription(7L, "2026-08-10");
        var reactivated = tools.reactivateSubscription(7L, "2026-09-01");

        assertThat(cancelled.cancelledOn()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(reactivated.active()).isTrue();
        assertThat(reactivated.nextDueOn()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void recordChargeRejectsCancelledSubscription() {
        Subscription subscription = subscription(7L, "GitHub", null, null);
        subscription.setCancelledOn(LocalDate.of(2026, 8, 1));
        when(subscriptions.findById(7L)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> tools.recordSubscriptionCharge(7L, "2026-08-10"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cancelled subscriptions cannot record charges");
    }

    @Test
    void recordChargeDelegatesForActiveSubscription() {
        Subscription subscription = subscription(7L, "GitHub", null, null);
        Expense expense = expense(3L);
        expense.setSubscription(subscription);
        when(subscriptions.findById(7L)).thenReturn(Optional.of(subscription));
        when(subscriptionService.recordCharge(subscription, LocalDate.of(2026, 8, 10)))
                .thenReturn(expense);

        var result = tools.recordSubscriptionCharge(7L, "2026-08-10");

        assertThat(result.id()).isEqualTo(3L);
        assertThat(result.subscriptionId()).isEqualTo(7L);
        verify(subscriptionService).recordCharge(subscription, LocalDate.of(2026, 8, 10));
    }

    @Test
    void linkAndUnlinkExpenseReturnSubscriptionRelationship() {
        Subscription subscription = subscription(7L, "GitHub", null, null);
        Expense expense = expense(3L);
        when(subscriptions.findById(7L)).thenReturn(Optional.of(subscription));
        when(expenses.findById(3L)).thenReturn(Optional.of(expense));
        when(subscriptionService.linkExpense(expense, subscription)).thenAnswer(invocation -> {
            expense.setSubscription(subscription);
            return expense;
        });
        when(subscriptionService.unlinkExpense(expense)).thenAnswer(invocation -> {
            expense.setSubscription(null);
            return expense;
        });

        var linked = tools.linkExpenseToSubscription(3L, 7L);
        var unlinked = tools.unlinkExpenseFromSubscription(3L);

        assertThat(linked.subscriptionId()).isEqualTo(7L);
        assertThat(linked.subscriptionVendor()).isEqualTo("GitHub");
        assertThat(unlinked.subscriptionId()).isNull();
        verify(subscriptionService).linkExpense(expense, subscription);
        verify(subscriptionService).unlinkExpense(expense);
    }

    private static Client client(Long id, String name) {
        Client client = new Client();
        client.setId(id);
        client.setName(name);
        return client;
    }

    private static Project project(Long id, String name, Client client) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setClient(client);
        return project;
    }

    private static Subscription subscription(Long id, String vendor, Client client, Project project) {
        Subscription subscription = new Subscription();
        subscription.setId(id);
        subscription.setVendor(vendor);
        subscription.setClient(client);
        subscription.setProject(project);
        subscription.setDescription("Recurring service");
        subscription.setCategory("software");
        subscription.setAmount(new BigDecimal("20.00"));
        subscription.setCurrency("USD");
        subscription.setCycle("monthly");
        subscription.setStartedOn(LocalDate.of(2026, 8, 1));
        subscription.setNextDueOn(LocalDate.of(2026, 9, 1));
        return subscription;
    }

    private static Expense expense(Long id) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setIncurredOn(LocalDate.of(2026, 8, 10));
        expense.setAmount(new BigDecimal("20.00"));
        expense.setCurrency("USD");
        expense.setExchangeRate(BigDecimal.ONE);
        expense.setCategory("software");
        expense.setBillable(false);
        expense.setBilled(false);
        return expense;
    }
}
