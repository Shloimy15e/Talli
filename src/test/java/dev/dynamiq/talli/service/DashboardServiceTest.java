package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Payment;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.PaymentRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.dto.DashboardCharts.BillableBreakdown;
import dev.dynamiq.talli.service.dto.DashboardCharts.DayMinutes;
import dev.dynamiq.talli.service.dto.DashboardCharts.MonthFinancials;
import dev.dynamiq.talli.service.dto.DashboardCharts.ProjectMinutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private ClientRepository clientRepository;
    private ProjectRepository projectRepository;
    private TimeEntryRepository timeEntryRepository;
    private ExpenseRepository expenseRepository;
    private SubscriptionRepository subscriptionRepository;
    private InvoiceRepository invoiceRepository;
    private PaymentRepository paymentRepository;
    private DashboardService service;

    private Project projectA;
    private Project projectB;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        projectRepository = mock(ProjectRepository.class);
        timeEntryRepository = mock(TimeEntryRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        paymentRepository = mock(PaymentRepository.class);

        projectA = new Project();
        projectA.setId(1L);
        projectA.setName("Alpha");
        projectA.setCurrentRate(new BigDecimal("100"));

        projectB = new Project();
        projectB.setId(2L);
        projectB.setName("Bravo");
        projectB.setCurrentRate(new BigDecimal("150"));

        service = new DashboardService(
                clientRepository, projectRepository, timeEntryRepository,
                expenseRepository, subscriptionRepository,
                invoiceRepository, paymentRepository);
    }

    @Test
    void countActiveProjects_onlyCountsActive() {
        Project active1 = new Project(); active1.setStatus("active");
        Project active2 = new Project(); active2.setStatus("active");
        Project archived = new Project(); archived.setStatus("archived");
        when(projectRepository.findAll()).thenReturn(List.of(active1, active2, archived));

        assertThat(service.countActiveProjects()).isEqualTo(2);
    }

    @Test
    void minutesThisWeek_sumsEntriesStartedOnOrAfterMonday() {
        LocalDateTime weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();

        TimeEntry thisWeek = entry(projectA, weekStart.plusHours(9), weekStart.plusHours(10), 60, true, false);
        TimeEntry earlyThisWeek = entry(projectA, weekStart.plusMinutes(30), weekStart.plusHours(1), 30, true, false);
        TimeEntry lastWeek = entry(projectA, weekStart.minusDays(2), weekStart.minusDays(2).plusHours(2), 120, true, false);

        when(timeEntryRepository.findAll()).thenReturn(List.of(thisWeek, earlyThisWeek, lastWeek));

        assertThat(service.minutesThisWeek()).isEqualTo(90);
    }

    @Test
    void unbilledMinutes_excludesNonBillableAndBilled() {
        TimeEntry unbilled = entry(projectA, null, null, 60, true, false);
        TimeEntry billed = entry(projectA, null, null, 45, true, true);
        TimeEntry nonBillable = entry(projectA, null, null, 30, false, false);
        TimeEntry runningUnbilled = entry(projectA, null, null, null, true, false);

        when(timeEntryRepository.findAll()).thenReturn(List.of(unbilled, billed, nonBillable, runningUnbilled));

        assertThat(service.unbilledMinutes()).isEqualTo(60);
    }

    @Test
    void recentEntries_sortsByStartedAtDescAndLimits() {
        TimeEntry oldest = entry(projectA, LocalDateTime.of(2026, 4, 10, 9, 0), null, 30, true, false);
        TimeEntry middle = entry(projectA, LocalDateTime.of(2026, 4, 12, 9, 0), null, 30, true, false);
        TimeEntry newest = entry(projectA, LocalDateTime.of(2026, 4, 14, 9, 0), null, 30, true, false);

        when(timeEntryRepository.findAll()).thenReturn(List.of(oldest, newest, middle));

        List<TimeEntry> recent = service.recentEntries(2);

        assertThat(recent).containsExactly(newest, middle);
    }

    @Test
    void timePerDay_bucketsIntoConsecutiveDaysEvenWhenZero() {
        LocalDate today = LocalDate.now();
        TimeEntry todayEntry = entry(projectA, today.atTime(9, 0), today.atTime(10, 0), 60, true, false);
        TimeEntry twoDaysAgo = entry(projectA, today.minusDays(2).atTime(9, 0), today.minusDays(2).atTime(9, 30), 30, true, false);
        TimeEntry outsideRange = entry(projectA, today.minusDays(30).atTime(9, 0), today.minusDays(30).atTime(10, 0), 60, true, false);

        when(timeEntryRepository.findAll()).thenReturn(List.of(todayEntry, twoDaysAgo, outsideRange));

        List<DayMinutes> result = service.timePerDay(7);

        assertThat(result).hasSize(7);
        assertThat(result.get(0).day()).isEqualTo(today.minusDays(6));
        assertThat(result.get(6).day()).isEqualTo(today);
        assertThat(result.get(6).minutes()).isEqualTo(60);
        assertThat(result.get(4).minutes()).isEqualTo(30); // two days ago = index 4 of 7
        assertThat(result.stream().mapToInt(DayMinutes::minutes).sum()).isEqualTo(90);
    }

    @Test
    void timePerProject_groupsByProjectNameSortedDesc() {
        LocalDate today = LocalDate.now();
        TimeEntry a1 = entry(projectA, today.atTime(9, 0), today.atTime(10, 0), 60, true, false);
        TimeEntry a2 = entry(projectA, today.atTime(11, 0), today.atTime(11, 30), 30, true, false);
        TimeEntry b1 = entry(projectB, today.atTime(14, 0), today.atTime(16, 0), 120, true, false);

        when(timeEntryRepository.findAll()).thenReturn(List.of(a1, a2, b1));

        List<ProjectMinutes> result = service.timePerProject(today.minusDays(1), today);

        assertThat(result).containsExactly(
                new ProjectMinutes("Bravo", 120),
                new ProjectMinutes("Alpha", 90));
    }

    @Test
    void revenueVsExpenses_sourcesFromInvoicesAndPayments() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);

        // Invoice issued this month for $500 — should count as invoiced revenue.
        Invoice inv = new Invoice();
        inv.setIssuedAt(firstOfThisMonth);
        inv.setAmount(new BigDecimal("500.00"));
        inv.setStatus("unpaid");

        // Void invoice — should NOT count.
        Invoice voided = new Invoice();
        voided.setIssuedAt(firstOfThisMonth);
        voided.setAmount(new BigDecimal("200.00"));
        voided.setStatus("void");

        // Payment received this month for $300.
        Payment pmt = new Payment();
        pmt.setPaidAt(firstOfThisMonth.plusDays(5));
        pmt.setAmount(new BigDecimal("300.00"));

        Expense ex = new Expense();
        ex.setAmount(new BigDecimal("40.00"));
        ex.setIncurredOn(firstOfThisMonth.plusDays(1));

        when(invoiceRepository.findAll()).thenReturn(List.of(inv, voided));
        when(paymentRepository.findAll()).thenReturn(List.of(pmt));
        when(expenseRepository.findByIncurredOnBetweenOrderByIncurredOnDesc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(ex));

        List<MonthFinancials> result = service.revenueVsExpenses(3);

        assertThat(result).hasSize(3);
        MonthFinancials thisMonth = result.get(2);
        assertThat(thisMonth.invoiced()).isEqualByComparingTo("500.00");  // void excluded
        assertThat(thisMonth.received()).isEqualByComparingTo("300.00");
        assertThat(thisMonth.expenses()).isEqualByComparingTo("40.00");
        assertThat(thisMonth.net()).isEqualByComparingTo("460.00");  // invoiced - expenses
    }

    @Test
    void billableBreakdown_splitsByBillableFlag() {
        TimeEntry billable1 = entry(projectA, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1), 60, true, false);
        TimeEntry billable2 = entry(projectA, LocalDateTime.now().minusHours(4), LocalDateTime.now().minusHours(3), 60, true, false);
        TimeEntry nonBillable = entry(projectA, LocalDateTime.now().minusHours(6), LocalDateTime.now().minusHours(5), 60, false, false);

        when(timeEntryRepository.findAll()).thenReturn(List.of(billable1, billable2, nonBillable));

        BillableBreakdown b = service.billableBreakdown();

        assertThat(b.billable()).isEqualTo(120);
        assertThat(b.nonBillable()).isEqualTo(60);
    }

    private static TimeEntry entry(Project project, LocalDateTime startedAt, LocalDateTime endedAt,
                                   Integer durationMinutes, boolean billable, boolean billed) {
        TimeEntry e = new TimeEntry();
        e.setProject(project);
        e.setStartedAt(startedAt);
        e.setEndedAt(endedAt);
        e.setDurationMinutes(durationMinutes);
        e.setBillable(billable);
        e.setBilled(billed);
        return e;
    }
}
