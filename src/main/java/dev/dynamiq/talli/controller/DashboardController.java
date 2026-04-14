package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Controller
public class DashboardController {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final ExpenseRepository expenseRepository;
    private final SubscriptionRepository subscriptionRepository;

    public DashboardController(ClientRepository clientRepository,
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

    @GetMapping({"/", "/dashboard"})
    public String index(Model model) {
        // Counts
        long clientsCount = clientRepository.count();
        long projectsCount = projectRepository.count();
        long activeProjects = projectRepository.findAll().stream()
                .filter(p -> "active".equals(p.getStatus())).count();

        // Time this week (Monday 00:00 → now)
        LocalDateTime weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<TimeEntry> allEntries = timeEntryRepository.findAll();
        int minutesThisWeek = allEntries.stream()
                .filter(e -> e.getStartedAt() != null && !e.getStartedAt().isBefore(weekStart))
                .mapToInt(e -> {
                    if (e.getDurationMinutes() != null) return e.getDurationMinutes();
                    if (e.getEndedAt() != null) return (int) ChronoUnit.MINUTES.between(e.getStartedAt(), e.getEndedAt());
                    // Running entry — count up to now
                    return (int) ChronoUnit.MINUTES.between(e.getStartedAt(), now);
                }).sum();

        int unbilledMinutes = allEntries.stream()
                .filter(e -> Boolean.TRUE.equals(e.getBillable())
                        && Boolean.FALSE.equals(e.getBilled())
                        && e.getDurationMinutes() != null)
                .mapToInt(TimeEntry::getDurationMinutes).sum();

        // Currently running timer
        TimeEntry running = timeEntryRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc().orElse(null);

        // Recent entries (last 5)
        List<TimeEntry> recentEntries = allEntries.stream()
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .limit(5)
                .toList();

        // Money-out tiles
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        var expensesThisMonth = expenseRepository.sumAmountBetween(monthStart, today);
        var monthlyBurn = subscriptionRepository.sumActiveMonthlyEquivalent();
        var subsDueThisWeek = subscriptionRepository
                .findByCancelledOnIsNullAndNextDueOnLessThanEqualOrderByNextDueOnAsc(today.plusDays(7));

        model.addAttribute("clientsCount", clientsCount);
        model.addAttribute("projectsCount", projectsCount);
        model.addAttribute("activeProjects", activeProjects);
        model.addAttribute("hoursThisWeek", minutesThisWeek / 60);
        model.addAttribute("minutesThisWeekRemainder", minutesThisWeek % 60);
        model.addAttribute("unbilledHours", unbilledMinutes / 60);
        model.addAttribute("unbilledMinutesRemainder", unbilledMinutes % 60);
        model.addAttribute("running", running);
        model.addAttribute("recentEntries", recentEntries);
        model.addAttribute("expensesThisMonth", expensesThisMonth);
        model.addAttribute("monthlyBurn", monthlyBurn);
        model.addAttribute("subsDueThisWeekCount", subsDueThisWeek.size());

        return "dashboard/index";
    }
}
