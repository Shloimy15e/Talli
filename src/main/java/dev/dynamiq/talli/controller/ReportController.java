package dev.dynamiq.talli.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    public ReportController(ReportService reportService, ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "12") int months, Model model) throws JsonProcessingException {
        LocalDate today = LocalDate.now();
        LocalDate yearStart = today.withMonth(1).withDayOfMonth(1);

        // Time utilization
        model.addAttribute("utilization", reportService.timeUtilization(yearStart, today));

        // Monthly revenue chart + table
        var monthly = reportService.monthlyRevenue(months);
        model.addAttribute("monthlyRevenue", monthly);
        model.addAttribute("monthlyRevenueJson", objectMapper.writeValueAsString(monthly));

        // Expense breakdown chart
        var expByCat = reportService.expensesByCategory(yearStart, today);
        model.addAttribute("expensesByCategory", expByCat);
        model.addAttribute("expensesByCategoryJson", objectMapper.writeValueAsString(expByCat));

        // Per-client P&L
        model.addAttribute("clientPL", reportService.clientProfitLoss(yearStart, today));

        // Revenue by project
        model.addAttribute("projectRevenue", reportService.revenueByProject(yearStart, today));

        // AR aging + outstanding invoices
        model.addAttribute("arAging", reportService.accountsReceivableAging());
        model.addAttribute("outstandingInvoices", reportService.outstandingInvoices());

        // Payment history
        model.addAttribute("payments", reportService.paymentHistory(yearStart, today));

        model.addAttribute("months", months);
        model.addAttribute("yearLabel", today.getYear());
        return "reports/index";
    }
}
