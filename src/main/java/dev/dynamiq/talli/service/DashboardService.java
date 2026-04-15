package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
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

    public DashboardService(ClientRepository clientRepository,
                            ProjectRepository projectRepository,
                            TimeEntryRepository timeEntryRepository,
                            ExpenseRepository expenseRepository,
                            SubscriptionRepository subscriptionRepository) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.expenseRepository = expenseRepository;
        this.subscriptionRepository = subscriptionRepository;
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

    public List<MonthFinancials> revenueVsExpenses(int months) {
        // Revenue proxy: billable time × project.currentRate (no invoicing yet — swap to billed=true when invoicing lands).
        LocalDate today = LocalDate.now();
        YearMonth startMonth = YearMonth.from(today).minusMonths(months - 1L);

        Map<YearMonth, BigDecimal> revenueByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> expensesByMonth = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            revenueByMonth.put(ym, BigDecimal.ZERO);
            expensesByMonth.put(ym, BigDecimal.ZERO);
        }

        LocalDateTime now = LocalDateTime.now();
        for (TimeEntry e : timeEntryRepository.findAll()) {
            if (!Boolean.TRUE.equals(e.getBillable())) continue;
            if (e.getStartedAt() == null || e.getProject() == null) continue;
            YearMonth ym = YearMonth.from(e.getStartedAt());
            if (!revenueByMonth.containsKey(ym)) continue;

            BigDecimal rate = e.getProject().getCurrentRate();
            if (rate == null) continue;
            BigDecimal minutes = BigDecimal.valueOf(minutesFor(e, now));
            BigDecimal amount = rate.multiply(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            revenueByMonth.merge(ym, amount, BigDecimal::add);
        }

        LocalDate rangeStart = startMonth.atDay(1);
        for (var ex : expenseRepository.findByIncurredOnBetweenOrderByIncurredOnDesc(rangeStart, today)) {
            if (ex.getAmount() == null || ex.getIncurredOn() == null) continue;
            YearMonth ym = YearMonth.from(ex.getIncurredOn());
            expensesByMonth.merge(ym, ex.getAmount(), BigDecimal::add);
        }

        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");
        List<MonthFinancials> out = new ArrayList<>(months);
        for (YearMonth ym : revenueByMonth.keySet()) {
            BigDecimal rev = revenueByMonth.get(ym);
            BigDecimal exp = expensesByMonth.get(ym);
            out.add(new MonthFinancials(ym.format(monthFmt), rev, exp, rev.subtract(exp)));
        }
        return out;
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
