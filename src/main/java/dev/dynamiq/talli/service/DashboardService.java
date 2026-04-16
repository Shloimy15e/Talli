package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Payment;
import dev.dynamiq.talli.model.Subscription;
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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DashboardService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final ExpenseRepository expenseRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExchangeRateService exchangeRateService;

    public DashboardService(ClientRepository clientRepository,
                            ProjectRepository projectRepository,
                            TimeEntryRepository timeEntryRepository,
                            ExpenseRepository expenseRepository,
                            SubscriptionRepository subscriptionRepository,
                            InvoiceRepository invoiceRepository,
                            PaymentRepository paymentRepository,
                            ExchangeRateService exchangeRateService) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.expenseRepository = expenseRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.exchangeRateService = exchangeRateService;
    }

    public long countClients() {
        return clientRepository.count();
    }

    public long countProjects() {
        return projectRepository.count();
    }

    public long countActiveProjects() {
        return projectRepository.findAll().stream()
                .filter(p -> "active".equals(p.getStatus()))
                .count();
    }

    public int minutesThisWeek() {
        LocalDateTime weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        return timeEntryRepository.findAll().stream()
                .filter(e -> e.getStartedAt() != null && !e.getStartedAt().isBefore(weekStart))
                .mapToInt(e -> minutesFor(e, now))
                .sum();
    }

    public int unbilledMinutes() {
        return timeEntryRepository.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.getBillable())
                        && Boolean.FALSE.equals(e.getBilled())
                        && e.getDurationMinutes() != null)
                .mapToInt(TimeEntry::getDurationMinutes)
                .sum();
    }

    public Optional<TimeEntry> findRunningTimer() {
        return timeEntryRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc();
    }

    public List<TimeEntry> recentEntries(int limit) {
        return timeEntryRepository.findAll().stream()
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .limit(limit)
                .toList();
    }

    public BigDecimal expensesThisMonth() {
        LocalDate today = LocalDate.now();
        return expenseRepository.sumAmountBetween(today.withDayOfMonth(1), today);
    }

    public BigDecimal monthlyBurn() {
        return subscriptionRepository.sumActiveMonthlyEquivalent();
    }

    public List<Subscription> subscriptionsDueWithin(int days) {
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return subscriptionRepository
                .findByCancelledOnIsNullAndNextDueOnLessThanEqualOrderByNextDueOnAsc(cutoff);
    }

    // --- Chart data ---

    public List<DayMinutes> timePerDay(int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);
        LocalDateTime now = LocalDateTime.now();

        Map<LocalDate, Integer> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) byDay.put(from.plusDays(i), 0);

        for (TimeEntry e : timeEntryRepository.findAll()) {
            if (e.getStartedAt() == null) continue;
            LocalDate day = e.getStartedAt().toLocalDate();
            if (day.isBefore(from) || day.isAfter(today)) continue;
            byDay.merge(day, minutesFor(e, now), Integer::sum);
        }

        return byDay.entrySet().stream()
                .map(en -> new DayMinutes(en.getKey(), en.getValue()))
                .toList();
    }

    public List<ProjectMinutes> timePerProject(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Integer> byProject = new LinkedHashMap<>();
        for (TimeEntry e : timeEntryRepository.findAll()) {
            if (e.getStartedAt() == null) continue;
            if (e.getStartedAt().isBefore(fromDt) || !e.getStartedAt().isBefore(toDt)) continue;
            String name = e.getProject() != null ? e.getProject().getName() : "Unassigned";
            byProject.merge(name, minutesFor(e, now), Integer::sum);
        }

        return byProject.entrySet().stream()
                .map(en -> new ProjectMinutes(en.getKey(), en.getValue()))
                .sorted(Comparator.comparingInt(ProjectMinutes::minutes).reversed())
                .toList();
    }

    /**
     * Revenue (invoiced, accrual) vs expenses by month. Revenue = sum of
     * non-void invoice amounts bucketed by issuedAt month. Expenses = sum of
     * expense amounts bucketed by incurredOn month.
     */
    public List<MonthFinancials> revenueVsExpenses(int months) {
        LocalDate today = LocalDate.now();
        YearMonth startMonth = YearMonth.from(today).minusMonths(months - 1L);

        Map<YearMonth, BigDecimal> invoicedByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> receivedByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> expensesByMonth = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            invoicedByMonth.put(ym, BigDecimal.ZERO);
            receivedByMonth.put(ym, BigDecimal.ZERO);
            expensesByMonth.put(ym, BigDecimal.ZERO);
        }

        // Invoiced revenue — bucketed by issuedAt month, converted to USD.
        for (Invoice inv : invoiceRepository.findAll()) {
            if ("void".equals(inv.getStatus())) continue;
            if (inv.getIssuedAt() == null || inv.getAmount() == null) continue;
            YearMonth ym = YearMonth.from(inv.getIssuedAt());
            BigDecimal usd = exchangeRateService.toUsd(inv.getAmount(), inv.getCurrency(), inv.getExchangeRate());
            invoicedByMonth.computeIfPresent(ym, (k, v) -> v.add(usd));
        }

        // Cash received — bucketed by paidAt month, converted to USD at payment-date rate.
        for (Payment p : paymentRepository.findAll()) {
            if (p.getPaidAt() == null || p.getAmount() == null) continue;
            YearMonth ym = YearMonth.from(p.getPaidAt());
            BigDecimal usd = exchangeRateService.toUsd(p.getAmount(), p.getInvoice().getCurrency(), p.getExchangeRate());
            receivedByMonth.computeIfPresent(ym, (k, v) -> v.add(usd));
        }

        // Expenses.
        LocalDate rangeStart = startMonth.atDay(1);
        for (var ex : expenseRepository.findByIncurredOnBetweenOrderByIncurredOnDesc(rangeStart, today)) {
            if (ex.getAmount() == null || ex.getIncurredOn() == null) continue;
            YearMonth ym = YearMonth.from(ex.getIncurredOn());
            expensesByMonth.computeIfPresent(ym, (k, v) -> v.add(ex.getAmount()));
        }

        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");
        List<MonthFinancials> out = new ArrayList<>(months);
        for (YearMonth ym : invoicedByMonth.keySet()) {
            BigDecimal inv = invoicedByMonth.get(ym);
            BigDecimal rec = receivedByMonth.get(ym);
            BigDecimal exp = expensesByMonth.get(ym);
            out.add(new MonthFinancials(ym.format(monthFmt), inv, rec, exp, rec.subtract(exp)));
        }
        return out;
    }

    /** Total outstanding balance across all unpaid/overdue invoices, converted to USD at current rate. */
    public BigDecimal totalReceivables() {
        return invoiceRepository.findAll().stream()
                .filter(i -> "unpaid".equals(i.getStatus()) || "overdue".equals(i.getStatus()))
                .map(i -> exchangeRateService.toUsdCurrent(i.balance(), i.getCurrency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BillableBreakdown billableBreakdown() {
        LocalDateTime now = LocalDateTime.now();
        int billable = 0;
        int nonBillable = 0;
        for (TimeEntry e : timeEntryRepository.findAll()) {
            if (e.getStartedAt() == null) continue;
            int m = minutesFor(e, now);
            if (Boolean.TRUE.equals(e.getBillable())) billable += m;
            else nonBillable += m;
        }
        return new BillableBreakdown(billable, nonBillable);
    }

    private static int minutesFor(TimeEntry e, LocalDateTime now) {
        if (e.getDurationMinutes() != null) return e.getDurationMinutes();
        if (e.getEndedAt() != null) return (int) ChronoUnit.MINUTES.between(e.getStartedAt(), e.getEndedAt());
        return (int) ChronoUnit.MINUTES.between(e.getStartedAt(), now);
    }
}
