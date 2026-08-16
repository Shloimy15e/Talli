package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@RefreshDatabaseTest
class ExpenseFilteringIntegrationTest {

    @Autowired
    private ClientRepository clients;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private ExpenseRepository expenses;

    @Test
    void filtersBySearchOwnershipSourceBillingAndDate() {
        Client acme = client("Acme Studio");
        Client beta = client("Beta Group");
        Project website = project("Website launch", acme);
        Subscription github = subscription("GitHub", acme, website);

        Expense subscriptionExpense = expense("Code hosting", LocalDate.of(2026, 8, 10));
        subscriptionExpense.setClient(acme);
        subscriptionExpense.setProject(website);
        subscriptionExpense.setSubscription(github);
        subscriptionExpense.setBillable(true);

        Expense mercuryExpense = expense("Cloud service", LocalDate.of(2026, 8, 20));
        mercuryExpense.setClient(acme);
        mercuryExpense.setMercuryTransactionId("01234567-89ab-cdef-0123-456789abcdef");
        mercuryExpense.setBillable(true);
        mercuryExpense.setBilled(true);

        Expense manualExpense = expense("Team lunch", LocalDate.of(2026, 7, 15));
        manualExpense.setClient(beta);
        manualExpense.setCategory("meals");

        expenses.saveAllAndFlush(java.util.List.of(subscriptionExpense, mercuryExpense, manualExpense));

        assertIds(find("github", null, null, "", "", "", null, null), subscriptionExpense);
        assertIds(find("acme", null, null, "", "", "", null, null),
                mercuryExpense, subscriptionExpense);
        assertIds(find("", acme.getId(), website.getId(), "", "", "", null, null),
                subscriptionExpense);
        assertIds(find("", null, null, "", "subscription", "unbilled", null, null),
                subscriptionExpense);
        assertIds(find("", null, null, "software", "mercury", "billed", null, null),
                mercuryExpense);
        assertIds(find("", null, null, "", "manual", "nonbillable", null, null),
                manualExpense);
        assertIds(find("", null, null, "", "", "",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
                mercuryExpense, subscriptionExpense);

        Page<Expense> paged = expenses.findFiltered(
                "acme", null, null, "", "", "", null, null, PageRequest.of(0, 1));
        assertThat(paged.getTotalElements()).isEqualTo(2);
        assertThat(paged.getTotalPages()).isEqualTo(2);
    }

    private Page<Expense> find(String search, Long clientId, Long projectId, String category,
                               String source, String billing, LocalDate from, LocalDate to) {
        return expenses.findFiltered(search, clientId, projectId, category, source, billing,
                from, to, PageRequest.of(0, 25));
    }

    private static void assertIds(Page<Expense> page, Expense... expected) {
        assertThat(page.getContent()).extracting(Expense::getId)
                .containsExactly(java.util.Arrays.stream(expected).map(Expense::getId).toArray(Long[]::new));
    }

    private Client client(String name) {
        Client client = new Client();
        client.setName(name);
        return clients.saveAndFlush(client);
    }

    private Project project(String name, Client client) {
        Project project = new Project();
        project.setName(name);
        project.setClient(client);
        project.setRateType("hourly");
        project.setCurrentRate(new BigDecimal("150.00"));
        project.setCurrency("USD");
        return projects.saveAndFlush(project);
    }

    private Subscription subscription(String vendor, Client client, Project project) {
        Subscription subscription = new Subscription();
        subscription.setVendor(vendor);
        subscription.setClient(client);
        subscription.setProject(project);
        subscription.setCategory("software");
        subscription.setAmount(new BigDecimal("20.00"));
        subscription.setCurrency("USD");
        subscription.setCycle("monthly");
        subscription.setStartedOn(LocalDate.of(2026, 8, 1));
        return subscriptions.saveAndFlush(subscription);
    }

    private static Expense expense(String description, LocalDate incurredOn) {
        Expense expense = new Expense();
        expense.setVendor(description);
        expense.setDescription(description);
        expense.setIncurredOn(incurredOn);
        expense.setAmount(new BigDecimal("20.00"));
        expense.setCurrency("USD");
        expense.setExchangeRate(BigDecimal.ONE);
        expense.setCategory("software");
        expense.setBillable(false);
        expense.setBilled(false);
        return expense;
    }
}
