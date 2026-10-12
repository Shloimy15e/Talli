package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.reporting.ReportAccessService;
import dev.dynamiq.talli.reporting.ReportQuery;
import dev.dynamiq.talli.reporting.ReportQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static dev.dynamiq.talli.reporting.ReportQuery.*;

@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAuthority('view-reports')")
public class ReportController {
    private final ReportQueryService reports;
    private final ReportAccessService access;

    public ReportController(ReportQueryService reports, ReportAccessService access) {
        this.reports = reports;
        this.access = access;
    }

    @GetMapping("/overview")
    public String index(@RequestParam(required = false) String from,
                        @RequestParam(required = false) String to,
                        @RequestParam(required = false) Integer months, Model model) {
        if (months != null && (months < 1 || months > 120)) {
            throw new IllegalArgumentException("months must be between 1 and 120");
        }
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate start = parseDate(from, today.withDayOfYear(1));
        LocalDate end = parseDate(to, today);
        if (start.isAfter(end)) throw new IllegalArgumentException("From must be on or before to.");
        String allFrom = "1900-01-01";
        var outstanding = filters(filter("outstanding", "eq", "true"));
        var overdue = filters(filter("outstanding", "eq", "true"), filter("dueAt", "lte", today.minusDays(1).toString()));
        var direct = filters(filter("source", "eq", "direct"));
        var unbilled = filters(filter("rateType", "eq", "hourly"), filter("billable", "eq", "true"),
                filter("billed", "eq", "false"), filter("running", "eq", "false"));

        model.addAttribute("outstanding", reports.rows(aggregate("invoices", allFrom, today.toString(), outstanding, "balance")));
        model.addAttribute("overdue", reports.rows(aggregate("invoices", allFrom, today.toString(), overdue, "balance")));
        model.addAttribute("receipts", reports.rows(aggregate("payments", start.toString(), end.toString(), direct, "amount")));
        model.addAttribute("expenses", reports.rows(aggregate("expenses", start.toString(), end.toString(), null, "amount")));
        var work = new ReportQuery("time_entries", allFrom, today.toString(), List.of("currency"), unbilled,
                List.of("currency"), List.of(new Metric("hours", "sum"), new Metric("value", "sum")), null, 0, 500);
        model.addAttribute("unbilled", reports.rows(work));

        var invoiceDetails = details("invoices", allFrom, today.toString(), outstanding,
                List.of("id", "reference", "clientName", "dueAt", "currency", "balance"), List.of(new Sort("dueAt", "asc")), 50);
        var overdueDetails = details("invoices", allFrom, today.toString(), overdue,
                invoiceDetails.columns(), invoiceDetails.sort(), 5);
        model.addAttribute("overdueInvoices", reports.rows(overdueDetails));
        var projectWork = new ReportQuery("time_entries", allFrom, today.toString(), null, unbilled,
                List.of("projectId", "projectName", "clientName", "currency"),
                List.of(new Metric("hours", "sum"), new Metric("value", "sum")),
                List.of(new Sort("sum_hours", "desc")), 0, 5);
        model.addAttribute("unbilledProjects", reports.rows(projectWork));
        model.addAttribute("outstandingUrl", access.links(invoiceDetails).reportUrl());
        model.addAttribute("overdueUrl", access.links(overdueDetails).reportUrl());
        model.addAttribute("receiptsUrl", access.links(details("payments", start.toString(), end.toString(), direct,
                null, List.of(new Sort("paidAt", "desc")), 50)).reportUrl());
        model.addAttribute("expensesUrl", access.links(details("expenses", start.toString(), end.toString(), null,
                null, List.of(new Sort("incurredOn", "desc")), 50)).reportUrl());
        model.addAttribute("unbilledUrl", access.links(details("time_entries", allFrom, today.toString(), unbilled,
                null, List.of(new Sort("startedAt", "asc")), 50)).reportUrl());
        model.addAttribute("projectWorkUrl", access.links(projectWork).reportUrl());
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("today", today);
        var dateLabel = DateTimeFormatter.ofPattern("MMM d, yyyy");
        model.addAttribute("periodLabel", start.format(dateLabel) + " – " + end.format(dateLabel));
        model.addAttribute("yearStart", today.withDayOfYear(1));
        model.addAttribute("monthStart", today.withDayOfMonth(1));
        model.addAttribute("lastMonthStart", today.minusMonths(1).withDayOfMonth(1));
        model.addAttribute("lastMonthEnd", today.withDayOfMonth(1).minusDays(1));
        return "reports/index";
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return LocalDate.parse(value); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Dates must use valid ISO YYYY-MM-DD values."); }
    }

    private Filter filter(String field, String operator, String value) {
        return new Filter(field, operator, List.of(value));
    }

    private FilterGroup filters(Filter... filters) { return new FilterGroup("all", List.of(filters), List.of()); }

    private ReportQuery aggregate(String dataset, String from, String to, FilterGroup filters, String field) {
        return new ReportQuery(dataset, from, to, List.of("currency"), filters, List.of("currency"),
                List.of(new Metric(field, "sum")), null, 0, 500);
    }

    private ReportQuery details(String dataset, String from, String to, FilterGroup filters,
                                List<String> columns, List<Sort> sort, int limit) {
        return new ReportQuery(dataset, from, to, columns, filters, null, null, sort, 0, limit);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRange(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
