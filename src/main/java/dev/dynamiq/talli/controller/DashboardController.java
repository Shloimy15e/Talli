package dev.dynamiq.talli.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;

    public DashboardController(DashboardService dashboardService, ObjectMapper objectMapper) {
        this.dashboardService = dashboardService;
        this.objectMapper = objectMapper;
    }

    @GetMapping({"/", "/dashboard"})
    public String index(Model model) throws JsonProcessingException {
        int minutesThisWeek = dashboardService.minutesThisWeek();
        int unbilledMinutes = dashboardService.unbilledMinutes();

        model.addAttribute("clientsCount", dashboardService.countClients());
        model.addAttribute("projectsCount", dashboardService.countProjects());
        model.addAttribute("activeProjects", dashboardService.countActiveProjects());
        model.addAttribute("hoursThisWeek", minutesThisWeek / 60);
        model.addAttribute("minutesThisWeekRemainder", minutesThisWeek % 60);
        model.addAttribute("unbilledHours", unbilledMinutes / 60);
        model.addAttribute("unbilledMinutesRemainder", unbilledMinutes % 60);
        model.addAttribute("running", dashboardService.findRunningTimer().orElse(null));
        model.addAttribute("recentEntries", dashboardService.recentEntries(5));
        model.addAttribute("expensesThisMonth", dashboardService.expensesThisMonth());
        model.addAttribute("monthlyBurn", dashboardService.monthlyBurn());
        model.addAttribute("subsDueThisWeekCount", dashboardService.subscriptionsDueWithin(7).size());

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        model.addAttribute("timePerDayJson",
                objectMapper.writeValueAsString(dashboardService.timePerDay(14)));
        model.addAttribute("timePerProjectJson",
                objectMapper.writeValueAsString(dashboardService.timePerProject(monthStart, today)));
        model.addAttribute("revenueVsExpensesJson",
                objectMapper.writeValueAsString(dashboardService.revenueVsExpenses(6)));
        model.addAttribute("billableBreakdownJson",
                objectMapper.writeValueAsString(dashboardService.billableBreakdown()));

        model.addAttribute("totalReceivables", dashboardService.totalReceivables());

        return "dashboard/index";
    }
}
