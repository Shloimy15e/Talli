package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Payment;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReportService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final ProjectRepository projectRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ExchangeRateService exchangeRateService;

    public ReportService(InvoiceRepository invoiceRepository,
                         PaymentRepository paymentRepository,
                         ExpenseRepository expenseRepository,
                         TimeEntryRepository timeEntryRepository,
                         ProjectRepository projectRepository,
                         InvoiceItemRepository invoiceItemRepository,
                         ExchangeRateService exchangeRateService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.projectRepository = projectRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.exchangeRateService = exchangeRateService;
    }

    // --- Per-client P&L ---

    public List<ClientPL> clientProfitLoss(LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> invoicedByClient = new HashMap<>();
        Map<Long, BigDecimal> receivedByClient = new HashMap<>();
        Map<Long, BigDecimal> expensesByClient = new HashMap<>();
        Map<Long, String> clientNames = new HashMap<>();

        for (Invoice inv : invoiceRepository.findAll()) {
            if ("void".equals(inv.getStatus())) continue;
            if (inv.getIssuedAt() == null || inv.getIssuedAt().isBefore(from) || inv.getIssuedAt().isAfter(to)) continue;
            Long cid = inv.getClient().getId();
            clientNames.putIfAbsent(cid, inv.getClient().getName());
            invoicedByClient.merge(cid,
                    exchangeRateService.toUsd(inv.getAmount(), inv.getCurrency(), inv.getExchangeRate()),
                    BigDecimal::add);
        }

        for (Payment p : paymentRepository.findAll()) {
            if (p.getPaidAt() == null || p.getPaidAt().isBefore(from) || p.getPaidAt().isAfter(to)) continue;
            Long cid = p.getInvoice().getClient().getId();
            clientNames.putIfAbsent(cid, p.getInvoice().getClient().getName());
            receivedByClient.merge(cid,
                    exchangeRateService.toUsd(p.getAmount(), p.getInvoice().getCurrency(), p.getExchangeRate()),
                    BigDecimal::add);
        }

        for (var ex : expenseRepository.findAllByOrderByIncurredOnDesc()) {
            if (ex.getClient() == null) continue;
            if (ex.getIncurredOn() == null || ex.getIncurredOn().isBefore(from) || ex.getIncurredOn().isAfter(to)) continue;
            Long cid = ex.getClient().getId();
            clientNames.putIfAbsent(cid, ex.getClient().getName());
            BigDecimal usd = exchangeRateService.toUsd(ex.getAmount(), ex.getCurrency(), ex.getExchangeRate());
            expensesByClient.merge(cid, usd, BigDecimal::add);
        }

        Set<Long> allClientIds = new HashSet<>();
        allClientIds.addAll(invoicedByClient.keySet());
        allClientIds.addAll(receivedByClient.keySet());
        allClientIds.addAll(expensesByClient.keySet());

        return allClientIds.stream()
                .map(cid -> {
                    BigDecimal invoiced = invoicedByClient.getOrDefault(cid, BigDecimal.ZERO);
                    BigDecimal received = receivedByClient.getOrDefault(cid, BigDecimal.ZERO);
                    BigDecimal expenses = expensesByClient.getOrDefault(cid, BigDecimal.ZERO);
                    return new ClientPL(cid, clientNames.get(cid), invoiced, received, expenses,
                            invoiced.subtract(expenses));
                })
                .sorted(Comparator.comparing(ClientPL::invoiced).reversed())
                .toList();
    }

    // --- Monthly revenue summary ---

    public List<MonthSummary> monthlyRevenue(int months) {
        LocalDate today = LocalDate.now();
        YearMonth startMonth = YearMonth.from(today).minusMonths(months - 1L);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");

        Map<YearMonth, BigDecimal> invoiced = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> received = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> expenses = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            invoiced.put(ym, BigDecimal.ZERO);
            received.put(ym, BigDecimal.ZERO);
            expenses.put(ym, BigDecimal.ZERO);
        }

        for (Invoice inv : invoiceRepository.findAll()) {
            if ("void".equals(inv.getStatus()) || inv.getIssuedAt() == null) continue;
            YearMonth ym = YearMonth.from(inv.getIssuedAt());
            BigDecimal usd = exchangeRateService.toUsd(inv.getAmount(), inv.getCurrency(), inv.getExchangeRate());
            invoiced.computeIfPresent(ym, (k, v) -> v.add(usd));
        }

        for (Payment p : paymentRepository.findAll()) {
            if (p.getPaidAt() == null) continue;
            YearMonth ym = YearMonth.from(p.getPaidAt());
            BigDecimal usd = exchangeRateService.toUsd(p.getAmount(), p.getInvoice().getCurrency(), p.getExchangeRate());
            received.computeIfPresent(ym, (k, v) -> v.add(usd));
        }

        LocalDate rangeStart = startMonth.atDay(1);
        for (var ex : expenseRepository.findByIncurredOnBetweenOrderByIncurredOnDesc(rangeStart, today)) {
            if (ex.getAmount() == null || ex.getIncurredOn() == null) continue;
            YearMonth ym = YearMonth.from(ex.getIncurredOn());
            BigDecimal usd = exchangeRateService.toUsd(ex.getAmount(), ex.getCurrency(), ex.getExchangeRate());
            expenses.computeIfPresent(ym, (k, v) -> v.add(usd));
        }

        List<MonthSummary> out = new ArrayList<>();
        for (YearMonth ym : invoiced.keySet()) {
            BigDecimal inv = invoiced.get(ym);
            BigDecimal rec = received.get(ym);
            BigDecimal exp = expenses.get(ym);
            out.add(new MonthSummary(ym.format(fmt), inv, rec, exp, rec.subtract(exp)));
        }
        return out;
    }

    /** Aggregate monthly data into quarters. */
    public List<QuarterSummary> quarterlyRevenue(int months) {
        List<MonthSummary> monthly = monthlyRevenue(months);
        LocalDate today = LocalDate.now();
        YearMonth startMonth = YearMonth.from(today).minusMonths(months - 1L);

        Map<String, BigDecimal[]> quarters = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            int q = (ym.getMonthValue() - 1) / 3 + 1;
            String key = ym.getYear() + " Q" + q;
            quarters.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }

        int idx = 0;
        for (int i = 0; i < months; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            int q = (ym.getMonthValue() - 1) / 3 + 1;
            String key = ym.getYear() + " Q" + q;
            BigDecimal[] totals = quarters.get(key);
            if (idx < monthly.size()) {
                MonthSummary m = monthly.get(idx);
                totals[0] = totals[0].add(m.invoiced());
                totals[1] = totals[1].add(m.received());
                totals[2] = totals[2].add(m.expenses());
            }
            idx++;
        }

        return quarters.entrySet().stream()
                .map(e -> new QuarterSummary(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], e.getValue()[1].subtract(e.getValue()[2])))
                .toList();
    }

    /** Aggregate monthly data into years. */
    public List<YearSummary> yearlyRevenue(int months) {
        List<MonthSummary> monthly = monthlyRevenue(months);
        LocalDate today = LocalDate.now();
        YearMonth startMonth = YearMonth.from(today).minusMonths(months - 1L);

        Map<Integer, BigDecimal[]> years = new LinkedHashMap<>();
        int idx = 0;
        for (int i = 0; i < months; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            BigDecimal[] totals = years.computeIfAbsent(ym.getYear(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            if (idx < monthly.size()) {
                MonthSummary m = monthly.get(idx);
                totals[0] = totals[0].add(m.invoiced());
                totals[1] = totals[1].add(m.received());
                totals[2] = totals[2].add(m.expenses());
            }
            idx++;
        }

        return years.entrySet().stream()
                .map(e -> new YearSummary(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], e.getValue()[1].subtract(e.getValue()[2])))
                .toList();
    }

    // --- Time utilization ---

    public TimeUtilization timeUtilization(LocalDate from, LocalDate to) {
        LocalDateTime now = LocalDateTime.now();
        int billableMinutes = 0;
        int nonBillableMinutes = 0;

        for (TimeEntry e : timeEntryRepository.findAll()) {
            if (e.getStartedAt() == null) continue;
            LocalDate day = e.getStartedAt().toLocalDate();
            if (day.isBefore(from) || day.isAfter(to)) continue;

            int m = TimeEntryService.minutesFor(e, now);
            if (Boolean.TRUE.equals(e.getBillable())) {
                billableMinutes += m;
            } else {
                nonBillableMinutes += m;
            }
        }

        int total = billableMinutes + nonBillableMinutes;
        BigDecimal rate = total > 0
                ? BigDecimal.valueOf(billableMinutes).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new TimeUtilization(billableMinutes, nonBillableMinutes, total, rate);
    }

    // --- Accounts receivable aging (cross-client) ---

    public List<ARLine> accountsReceivableAging() {
        LocalDate today = LocalDate.now();
        List<ARLine> lines = new ArrayList<>();

        for (Invoice inv : invoiceRepository.findAll()) {
            if (!"unpaid".equals(inv.getStatus()) && !"overdue".equals(inv.getStatus())) continue;
            BigDecimal balance = inv.balance();
            if (balance.signum() <= 0) continue;

            long daysOutstanding = inv.getDueAt() != null
                    ? ChronoUnit.DAYS.between(inv.getDueAt(), today) : 0;
            String bucket;
            if (daysOutstanding <= 0) bucket = "Current";
            else if (daysOutstanding <= 30) bucket = "1-30";
            else if (daysOutstanding <= 60) bucket = "31-60";
            else if (daysOutstanding <= 90) bucket = "61-90";
            else bucket = "90+";

            lines.add(new ARLine(inv.getId(), inv.getReference(), inv.getClient().getName(),
                    inv.getIssuedAt(), inv.getDueAt(), inv.getAmount(), inv.getAmountPaid(),
                    balance, daysOutstanding, bucket, inv.getCurrency()));
        }

        lines.sort(Comparator.comparingLong(ARLine::daysOutstanding).reversed());
        return lines;
    }

    // --- Revenue by project ---

    public List<ProjectRevenue> revenueByProject(LocalDate from, LocalDate to) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, ProjectRevenue> byProject = new LinkedHashMap<>();

        for (Project p : projectRepository.findAll()) {
            BigDecimal billed = invoiceItemRepository.sumTotalByProjectId(p.getId());
            List<TimeEntry> entries = timeEntryRepository.findByProjectIdOrderByStartedAtDesc(p.getId());

            int totalMinutes = 0;
            int billableMinutes = 0;
            for (TimeEntry e : entries) {
                if (e.getStartedAt() == null) continue;
                LocalDate day = e.getStartedAt().toLocalDate();
                if (day.isBefore(from) || day.isAfter(to)) continue;
                int m = TimeEntryService.minutesFor(e, now);
                totalMinutes += m;
                if (Boolean.TRUE.equals(e.getBillable())) billableMinutes += m;
            }

            if (billed.signum() == 0 && totalMinutes == 0) continue; // skip inactive

            BigDecimal effectiveRate = billableMinutes > 0
                    ? billed.divide(BigDecimal.valueOf(billableMinutes).divide(
                            BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            byProject.put(p.getId(), new ProjectRevenue(
                    p.getId(), p.getName(), p.getClient().getName(),
                    p.getRateType(), p.getCurrency(),
                    billed, totalMinutes, billableMinutes, effectiveRate));
        }

        return byProject.values().stream()
                .sorted(Comparator.comparing(ProjectRevenue::billed).reversed())
                .toList();
    }

    // --- Expenses by category ---

    public List<CategoryExpense> expensesByCategory(LocalDate from, LocalDate to) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<String, Integer> countByCategory = new LinkedHashMap<>();

        for (Expense ex : expenseRepository.findByIncurredOnBetweenOrderByIncurredOnDesc(from, to)) {
            if (ex.getAmount() == null) continue;
            String cat = ex.getCategory() != null ? ex.getCategory() : "uncategorized";
            BigDecimal usd = exchangeRateService.toUsd(ex.getAmount(), ex.getCurrency(), ex.getExchangeRate());
            byCategory.merge(cat, usd, BigDecimal::add);
            countByCategory.merge(cat, 1, Integer::sum);
        }

        BigDecimal grandTotal = byCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return byCategory.entrySet().stream()
                .map(e -> {
                    BigDecimal pct = grandTotal.signum() > 0
                            ? e.getValue().multiply(BigDecimal.valueOf(100))
                                    .divide(grandTotal, 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return new CategoryExpense(e.getKey(), e.getValue(),
                            countByCategory.get(e.getKey()), pct);
                })
                .sorted(Comparator.comparing(CategoryExpense::total).reversed())
                .toList();
    }

    // --- Payment collection history ---

    public List<PaymentLine> paymentHistory(LocalDate from, LocalDate to) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getPaidAt() != null && !p.getPaidAt().isBefore(from) && !p.getPaidAt().isAfter(to))
                .sorted(Comparator.comparing(Payment::getPaidAt).reversed())
                .map(p -> new PaymentLine(
                        p.getId(), p.getInvoice().getReference(), p.getInvoice().getClient().getName(),
                        p.getPaidAt(), p.getAmount(), p.getMethod(), p.getReference(),
                        p.getInvoice().getCurrency()))
                .toList();
    }

    // --- Outstanding invoices ---

    public List<OutstandingInvoice> outstandingInvoices() {
        LocalDate today = LocalDate.now();
        return invoiceRepository.findAll().stream()
                .filter(i -> "unpaid".equals(i.getStatus()) || "overdue".equals(i.getStatus()))
                .filter(i -> i.balance().signum() > 0)
                .sorted(Comparator.comparing((Invoice i) -> i.getDueAt() != null ? i.getDueAt() : LocalDate.MAX))
                .map(i -> {
                    long days = i.getDueAt() != null ? ChronoUnit.DAYS.between(i.getDueAt(), today) : 0;
                    return new OutstandingInvoice(i.getId(), i.getReference(), i.getClient().getName(),
                            i.getIssuedAt(), i.getDueAt(), i.getAmount(), i.balance(),
                            days, i.getCurrency(), i.getSentAt() != null);
                })
                .toList();
    }

    // --- Records ---

    public record ClientPL(Long clientId, String clientName,
                           BigDecimal invoiced, BigDecimal received, BigDecimal expenses,
                           BigDecimal profit) {}

    public record MonthSummary(String month, BigDecimal invoiced, BigDecimal received,
                               BigDecimal expenses, BigDecimal net) {}

    public record QuarterSummary(String quarter, BigDecimal invoiced, BigDecimal received,
                                 BigDecimal expenses, BigDecimal net) {}

    public record YearSummary(int year, BigDecimal invoiced, BigDecimal received,
                              BigDecimal expenses, BigDecimal net) {}

    public record TimeUtilization(int billableMinutes, int nonBillableMinutes,
                                  int totalMinutes, BigDecimal utilizationRate) {}

    public record ARLine(Long invoiceId, String reference, String clientName,
                         LocalDate issuedAt, LocalDate dueAt,
                         BigDecimal amount, BigDecimal paid, BigDecimal balance,
                         long daysOutstanding, String bucket, String currency) {}

    public record ProjectRevenue(Long projectId, String projectName, String clientName,
                                 String rateType, String currency,
                                 BigDecimal billed, int totalMinutes, int billableMinutes,
                                 BigDecimal effectiveRate) {}

    public record CategoryExpense(String category, BigDecimal total, int count, BigDecimal percentage) {}

    public record PaymentLine(Long paymentId, String invoiceRef, String clientName,
                              LocalDate paidAt, BigDecimal amount, String method, String reference,
                              String currency) {}

    public record OutstandingInvoice(Long invoiceId, String reference, String clientName,
                                     LocalDate issuedAt, LocalDate dueAt,
                                     BigDecimal amount, BigDecimal balance,
                                     long daysOverdue, String currency, boolean sent) {}
}
