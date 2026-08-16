package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {

    private SubscriptionRepository subscriptionRepository;
    private ExpenseRepository expenseRepository;
    private ExpenseService expenseService;
    private ExchangeRateService exchangeRateService;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        expenseService = mock(ExpenseService.class);
        exchangeRateService = mock(ExchangeRateService.class);
        when(expenseService.create(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(exchangeRateService.toUsdCurrent(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        service = new SubscriptionService(subscriptionRepository, expenseRepository,
                expenseService, exchangeRateService);
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

        verify(expenseService).create(any(Expense.class));
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void monthlyBurnUsd_convertsYearlyTo12thAndAppliesCurrentRate() {
        Subscription monthly = monthlySub("Slack", new BigDecimal("10.00"), "software", LocalDate.of(2026, 1, 1));
        monthly.setCurrency("USD");
        Subscription yearly = yearlySub("Domain", new BigDecimal("120.00"), "software", LocalDate.of(2026, 1, 1));
        yearly.setCurrency("USD");
        Subscription eurMonthly = monthlySub("Hetzner", new BigDecimal("50.00"), "hosting", LocalDate.of(2026, 1, 1));
        eurMonthly.setCurrency("EUR");

        when(subscriptionRepository.findByCancelledOnIsNullOrderByVendorAsc())
                .thenReturn(java.util.List.of(monthly, yearly, eurMonthly));
        // USD → identity; EUR → current rate 2.0 (so 50 EUR becomes 25 USD).
        when(exchangeRateService.toUsdCurrent(any(), org.mockito.ArgumentMatchers.eq("USD")))
                .thenAnswer(inv -> inv.getArgument(0));
        when(exchangeRateService.toUsdCurrent(any(), org.mockito.ArgumentMatchers.eq("EUR")))
                .thenReturn(new BigDecimal("25.00"));

        BigDecimal total = service.monthlyBurnUsd();

        // 10 (monthly USD) + 120/12 = 10 (yearly USD) + 25 (EUR→USD) = 45
        assertThat(total).isEqualByComparingTo("45.00");
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

    @Test
    void reactivate_clearsCancellationAndSetsNextDue() {
        Subscription sub = monthlySub("OldTool", new BigDecimal("5.00"), "software",
                LocalDate.of(2025, 6, 1));
        sub.setCancelledOn(LocalDate.of(2026, 4, 14));

        service.reactivate(sub, LocalDate.of(2026, 5, 1));

        assertThat(sub.getCancelledOn()).isNull();
        assertThat(sub.getNextDueOn()).isEqualTo(LocalDate.of(2026, 5, 1));
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void delete_unlinksHistoricalExpensesBeforeDeletingTemplate() {
        Subscription sub = monthlySub("GitHub", new BigDecimal("20.00"), "software",
                LocalDate.of(2026, 1, 1));
        sub.setId(7L);
        when(expenseRepository.unlinkAllFromSubscription(7L)).thenReturn(3);

        int unlinked = service.delete(sub);

        assertThat(unlinked).isEqualTo(3);
        var order = inOrder(expenseRepository, subscriptionRepository);
        order.verify(expenseRepository).unlinkAllFromSubscription(7L);
        order.verify(subscriptionRepository).delete(sub);
    }

    @Test
    void linkExpense_inheritsCompatibleSubscriptionAssociations() {
        Client client = new Client();
        client.setId(1L);
        Project project = new Project();
        project.setId(2L);
        project.setClient(client);
        Subscription sub = monthlySub("GitHub", new BigDecimal("20.00"), "software",
                LocalDate.of(2026, 1, 1));
        sub.setId(7L);
        sub.setClient(client);
        sub.setProject(project);
        Expense expense = new Expense();

        Expense linked = service.linkExpense(expense, sub);

        assertThat(linked.getSubscription()).isSameAs(sub);
        assertThat(linked.getClient()).isSameAs(client);
        assertThat(linked.getProject()).isSameAs(project);
        verify(expenseRepository).save(expense);
    }

    @Test
    void linkExpense_rejectsCrossClientLink() {
        Client expenseClient = new Client();
        expenseClient.setId(1L);
        Client subscriptionClient = new Client();
        subscriptionClient.setId(2L);
        Expense expense = new Expense();
        expense.setClient(expenseClient);
        Subscription sub = monthlySub("GitHub", new BigDecimal("20.00"), "software",
                LocalDate.of(2026, 1, 1));
        sub.setClient(subscriptionClient);

        assertThatThrownBy(() -> service.linkExpense(expense, sub))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expense and subscription belong to different clients");
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void unlinkExpense_removesOnlySubscription() {
        Client client = new Client();
        client.setId(1L);
        Expense expense = new Expense();
        expense.setClient(client);
        expense.setSubscription(monthlySub("GitHub", new BigDecimal("20.00"), "software",
                LocalDate.of(2026, 1, 1)));

        Expense unlinked = service.unlinkExpense(expense);

        assertThat(unlinked.getSubscription()).isNull();
        assertThat(unlinked.getClient()).isSameAs(client);
        verify(expenseRepository).save(expense);
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
