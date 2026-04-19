package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.*;
import dev.dynamiq.talli.repository.*;
import dev.dynamiq.talli.service.ImportService.ParsedFile;
import dev.dynamiq.talli.service.ImportService.ParsedWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class MigrationService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ExpenseRepository expenseRepository;
    private final ExchangeRateService exchangeRateService;

    public MigrationService(ClientRepository clientRepository,
                            ProjectRepository projectRepository,
                            TimeEntryRepository timeEntryRepository,
                            InvoiceRepository invoiceRepository,
                            InvoiceItemRepository invoiceItemRepository,
                            PaymentRepository paymentRepository,
                            SubscriptionRepository subscriptionRepository,
                            ExpenseRepository expenseRepository,
                            ExchangeRateService exchangeRateService) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.expenseRepository = expenseRepository;
        this.exchangeRateService = exchangeRateService;
    }

    private long refCounter;

    private String nextReference() {
        refCounter++;
        if (refCounter < 1001L) refCounter = 1001L;
        return "INV-" + String.format("%04d", refCounter);
    }

    // ── Records ──

    // Preview DTOs — what will be created in the system
    public record PreviewTimeEntry(String date, String client, String project, int minutes,
                                       boolean billable, String charge, String currency, String notes) {}
    public record PreviewInvoice(String client, String project, String issuedAt, String dueAt, String paidAt,
                                 String currency, String amount, String amountPaid, String status) {}
    public record PreviewSubscription(String vendor, String description, String cost, String from, String to, int monthsOfExpenses) {}
    public record PreviewExpense(String vendor, String description, String cost, String date) {}

    public record MigrationPreview(
            List<String> rawClientNames,
            Map<String, String> suggestedMapping,
            List<String> warnings,
            List<PreviewTimeEntry> timeEntries,
            List<PreviewInvoice> invoices,
            List<PreviewSubscription> subscriptions,
            List<PreviewExpense> expenses
    ) {
        public int totalExpenses() {
            return subscriptions.stream().mapToInt(PreviewSubscription::monthsOfExpenses).sum()
                    + expenses.size();
        }
    }

    public record MigrationResult(
            int clientsCreated, int projectsCreated, int timeEntriesCreated,
            int invoicesCreated, int paymentsCreated, int subscriptionsCreated,
            int expensesCreated, List<String> errors
    ) {}

    // ── Preview ──

    public MigrationPreview buildPreview(ParsedWorkbook workbook) {
        List<String> warnings = new ArrayList<>();
        Set<String> rawNames = new LinkedHashSet<>();
        Map<String, String> clientMap;

        ParsedFile projectsSheet = workbook.sheets().get("Projects");
        ParsedFile invoicesSheet = workbook.sheets().get("Invoices");
        ParsedFile monthlySheet = workbook.sheets().get("Monthly Expenses");
        ParsedFile oneTimeSheet = workbook.sheets().get("One Time Expenses");

        // Collect client names
        if (projectsSheet != null) {
            for (var row : projectsSheet.rows()) {
                String c = SpreadsheetUtil.val(row, "Client");
                if (!c.isBlank()) rawNames.add(c);
            }
        } else {
            warnings.add("No 'Projects' sheet found (expected for time entries)");
        }
        if (invoicesSheet != null) {
            for (var row : invoicesSheet.rows()) {
                String c = SpreadsheetUtil.val(row, "Client");
                if (!c.isBlank()) rawNames.add(c);
            }
        } else {
            warnings.add("No 'Invoices' sheet found");
        }
        clientMap = ClientMapping.suggest(rawNames);

        // Build preview time entries
        List<PreviewTimeEntry> timeEntries = new ArrayList<>();
        if (projectsSheet != null) {
            for (var row : projectsSheet.rows()) {
                String client = SpreadsheetUtil.val(row, "Client");
                int minutes = SpreadsheetUtil.parseMinutes(SpreadsheetUtil.val(row, "Billable Minutes"));
                LocalDate date = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Date"));
                if (client.isBlank() || minutes <= 0 || date == null) continue;

                BigDecimal rate = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Rate"));
                BigDecimal charge = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Total Charge"));
                String projectName = deriveProjectName(client, rate, charge);
                boolean billable = charge.signum() > 0 || rate.signum() > 0;

                timeEntries.add(new PreviewTimeEntry(
                        date.toString(), clientMap.getOrDefault(client, client),
                        projectName, minutes, billable, charge.toPlainString(),
                        SpreadsheetUtil.currencyFromSymbol(SpreadsheetUtil.val(row, "Currency")),
                        SpreadsheetUtil.val(row, "Notes")));
            }
        }

        // Build preview invoices
        List<PreviewInvoice> invoices = new ArrayList<>();
        if (invoicesSheet != null) {
            for (var row : invoicesSheet.rows()) {
                String client = SpreadsheetUtil.val(row, "Client");
                BigDecimal amount = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Requested Amount"));
                LocalDate issuedAt = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Invoice Sent"));
                if (client.isBlank() || amount.signum() <= 0 || issuedAt == null) continue;

                BigDecimal paid = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Amount Paid"));
                LocalDate paidAt = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Paid At"));
                LocalDate dueAt = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Payment Due Date"));

                String status = "unpaid";
                if (paid.compareTo(amount) >= 0) status = "paid";
                else if (dueAt != null && dueAt.isBefore(LocalDate.now())) status = "overdue";

                invoices.add(new PreviewInvoice(
                        clientMap.getOrDefault(client, client),
                        SpreadsheetUtil.val(row, "Project"),
                        issuedAt.toString(),
                        dueAt != null ? dueAt.toString() : "",
                        paidAt != null ? paidAt.toString() : "",
                        SpreadsheetUtil.currencyFromSymbol(SpreadsheetUtil.val(row, "$")),
                        amount.toPlainString(), paid.toPlainString(), status));
            }
        }

        // Build preview subscriptions
        List<PreviewSubscription> subscriptions = new ArrayList<>();
        if (monthlySheet != null) {
            for (var row : monthlySheet.rows()) {
                String vendor = SpreadsheetUtil.val(row, "Name");
                String subDesc = SpreadsheetUtil.val(row, "Role");
                if (vendor.isBlank() && !subDesc.isBlank()) {
                    vendor = subDesc;
                    subDesc = "";
                }
                BigDecimal cost = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Cost"));
                LocalDate from = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "From"));
                if (vendor.isBlank() || cost.signum() <= 0 || from == null) continue;

                LocalDate to = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "To"));
                LocalDate end = (to != null && !to.isAfter(LocalDate.now())) ? to : LocalDate.now();
                int months = 0;
                LocalDate m = from;
                while (!m.isAfter(end)) { months++; m = m.plusMonths(1); }

                subscriptions.add(new PreviewSubscription(
                        vendor, subDesc,
                        cost.toPlainString(), from.toString(),
                        to != null ? to.toString() : "ongoing", months));
            }
        }

        // Build preview one-time expenses
        List<PreviewExpense> expenses = new ArrayList<>();
        if (oneTimeSheet != null) {
            for (var row : oneTimeSheet.rows()) {
                BigDecimal cost = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Cost"));
                LocalDate date = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Date"));
                if (cost.signum() <= 0 || date == null) continue;
                expenses.add(new PreviewExpense(
                        SpreadsheetUtil.val(row, "Name"), SpreadsheetUtil.val(row, "Role"),
                        cost.toPlainString(), date.toString()));
            }
        }

        return new MigrationPreview(
                new ArrayList<>(rawNames), clientMap, warnings,
                timeEntries, invoices, subscriptions, expenses);
    }

    // ── Import ──

    @Transactional
    public MigrationResult executeImport(ParsedWorkbook workbook, Map<String, String> clientMapping) {
        List<String> errors = new ArrayList<>();

        // Pre-compute a reference counter so we don't query the DB per invoice
        refCounter = invoiceRepository.findAll().stream()
                .map(Invoice::getReference)
                .filter(r -> r != null && r.startsWith("INV-"))
                .map(r -> r.substring(4))
                .filter(s -> s.matches("\\d+"))
                .mapToLong(Long::parseLong)
                .max().orElse(1000L);

        Map<String, Client> clientByRaw = upsertClients(clientMapping);
        Map<String, Project> projectCache = upsertProjects(workbook, clientByRaw);

        // Invoices first — time entries need to reference them
        int[] invPay = importInvoicesAndPayments(workbook.sheets().get("Invoices"), clientByRaw, projectCache, errors);

        // Build lookup for matching time entries to invoices: "clientId:date" → Invoice
        Map<String, Invoice> invoiceLookup = new HashMap<>();
        for (Invoice inv : invoiceRepository.findAll()) {
            if (inv.getIssuedAt() != null) {
                invoiceLookup.put(inv.getClient().getId() + ":" + inv.getIssuedAt(), inv);
            }
        }

        int timeCount = importTimeEntries(workbook.sheets().get("Projects"), clientByRaw, projectCache, invoiceLookup, errors);
        int[] subExp = importSubscriptionsAndExpenses(workbook.sheets().get("Monthly Expenses"), errors);
        int oneTimeExp = importOneTimeExpenses(workbook.sheets().get("One Time Expenses"), errors);

        int clientsCreated = (int) clientByRaw.values().stream().map(Client::getId).distinct().count();
        int projectsCreated = projectCache.size();

        return new MigrationResult(
                clientsCreated, projectsCreated, timeCount,
                invPay[0], invPay[1], subExp[0], subExp[1] + oneTimeExp, errors);
    }

    // ── Step 1: Clients ──

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}");

    private Map<String, Client> upsertClients(Map<String, String> clientMapping) {
        // Collect first email found per normalized name from raw names
        Map<String, String> emailByNormalized = new LinkedHashMap<>();
        for (var entry : clientMapping.entrySet()) {
            String normalized = entry.getValue();
            if (emailByNormalized.containsKey(normalized)) continue;
            var matcher = EMAIL_PATTERN.matcher(entry.getKey());
            if (matcher.find()) {
                emailByNormalized.put(normalized, matcher.group());
            }
        }

        Map<String, Client> byNormalized = new HashMap<>();
        for (String name : new LinkedHashSet<>(clientMapping.values())) {
            if (name.isBlank()) continue;
            byNormalized.computeIfAbsent(name, n -> {
                Client c = clientRepository.findByNameIgnoreCase(n).orElseGet(() -> {
                    Client newClient = new Client();
                    newClient.setName(n);
                    return newClient;
                });
                // Set email if missing and we found one
                if ((c.getEmail() == null || c.getEmail().isBlank())
                        && emailByNormalized.containsKey(n)) {
                    c.setEmail(emailByNormalized.get(n));
                }
                return clientRepository.save(c);
            });
        }

        Map<String, Client> byRaw = new HashMap<>();
        for (var entry : clientMapping.entrySet()) {
            Client c = byNormalized.get(entry.getValue());
            if (c != null) byRaw.put(entry.getKey(), c);
        }
        return byRaw;
    }

    // ── Step 2: Projects ──

    private Map<String, Project> upsertProjects(ParsedWorkbook workbook, Map<String, Client> clientByRaw) {
        Map<String, Project> cache = new HashMap<>();

        // From Invoices "Project" column — named projects
        ParsedFile invoices = workbook.sheets().get("Invoices");
        if (invoices != null) {
            for (var row : invoices.rows()) {
                String projectName = SpreadsheetUtil.val(row, "Project");
                Client client = clientByRaw.get(SpreadsheetUtil.val(row, "Client"));
                if (projectName.isBlank() || client == null) continue;

                String key = client.getId() + ":" + projectName;
                cache.computeIfAbsent(key, k -> findOrCreateProject(
                        client, projectName,
                        SpreadsheetUtil.currencyFromSymbol(SpreadsheetUtil.val(row, "$")),
                        BigDecimal.ZERO, "hourly"));
            }
        }

        // From time entries — use embedded project name if present, else derive from rate
        ParsedFile timeSheet = workbook.sheets().get("Projects");
        if (timeSheet != null) {
            for (var row : timeSheet.rows()) {
                String rawClient = SpreadsheetUtil.val(row, "Client");
                Client client = clientByRaw.get(rawClient);
                if (client == null) continue;

                BigDecimal rate = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Rate"));
                BigDecimal charge = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Total Charge"));
                String currency = SpreadsheetUtil.currencyFromSymbol(SpreadsheetUtil.val(row, "Currency"));

                String projectName = deriveProjectName(rawClient, rate, charge);
                String rateType = rate.signum() > 0 ? "hourly" : (charge.signum() > 0 ? "fixed" : "hourly");

                String key = client.getId() + ":" + projectName;
                BigDecimal finalRate = rate;
                cache.computeIfAbsent(key, k -> findOrCreateProject(
                        client, projectName, currency, finalRate, rateType));
            }
        }

        return cache;
    }

    /** Derive project name: embedded name from raw client > rate-based > fallback. */
    static String deriveProjectName(String rawClient, BigDecimal rate, BigDecimal charge) {
        String embedded = ClientMapping.extractProject(rawClient);
        if (embedded != null) return embedded;
        if (rate.signum() > 0) return "Hourly @" + rate.stripTrailingZeros().toPlainString() + "/hr";
        if (charge.signum() > 0) return "Fixed";
        return "Time Tracking";
    }

    private Project findOrCreateProject(Client client, String name, String currency,
                                        BigDecimal rate, String rateType) {
        return projectRepository.findAll().stream()
                .filter(p -> p.getClient().getId().equals(client.getId())
                        && p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setName(name);
                    p.setClient(client);
                    p.setRateType(rateType);
                    p.setCurrentRate(rate);
                    p.setCurrency(currency);
                    return projectRepository.save(p);
                });
    }

    // ── Step 3: Time entries ──

    private int importTimeEntries(ParsedFile sheet, Map<String, Client> clientByRaw,
                                  Map<String, Project> projectCache,
                                  Map<String, Invoice> invoiceLookup, List<String> errors) {
        if (sheet == null) return 0;
        int count = 0;
        for (int i = 0; i < sheet.rows().size(); i++) {
            var row = sheet.rows().get(i);
            try {
                Client client = clientByRaw.get(SpreadsheetUtil.val(row, "Client"));
                if (client == null) continue;

                int minutes = SpreadsheetUtil.parseMinutes(SpreadsheetUtil.val(row, "Billable Minutes"));
                LocalDate date = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Date"));
                if (minutes <= 0 || date == null) continue;

                BigDecimal rate = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Rate"));
                BigDecimal charge = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Total Charge"));
                String currency = SpreadsheetUtil.currencyFromSymbol(SpreadsheetUtil.val(row, "Currency"));

                String rawClient = SpreadsheetUtil.val(row, "Client");
                boolean billable = charge.signum() > 0 || rate.signum() > 0;

                // Try to match invoice first — its project takes priority
                String invoicedVal = SpreadsheetUtil.val(row, "Invoiced");
                Invoice matched = null;
                if (!invoicedVal.isBlank()) {
                    LocalDate invoicedDate = SpreadsheetUtil.parseDate(invoicedVal);
                    if (invoicedDate != null) {
                        matched = invoiceLookup.get(client.getId() + ":" + invoicedDate);
                    }
                }

                // Determine project: invoice's project > derived from name/rate
                Project project = null;
                if (matched != null) {
                    // Get the invoice's project from its items
                    List<InvoiceItem> items = invoiceItemRepository.findByInvoiceIdOrderByIdAsc(matched.getId());
                    if (!items.isEmpty() && items.get(0).getProject() != null) {
                        project = items.get(0).getProject();
                    }
                }
                if (project == null) {
                    String projectName = deriveProjectName(rawClient, rate, charge);
                    project = projectCache.get(client.getId() + ":" + projectName);
                }
                if (project == null) continue;

                TimeEntry te = new TimeEntry();
                te.setProject(project);
                te.setStartedAt(date.atStartOfDay());
                te.setEndedAt(date.atStartOfDay().plusMinutes(minutes));
                te.setDurationMinutes(minutes);
                te.setDescription(SpreadsheetUtil.val(row, "Notes"));
                te.setBillable(billable);

                if (matched != null) {
                    te.setInvoice(matched);
                    List<InvoiceItem> matchedItems = invoiceItemRepository.findByInvoiceIdOrderByIdAsc(matched.getId());
                    if (!matchedItems.isEmpty()) te.setInvoiceItem(matchedItems.get(0));
                    te.setBilled(true);
                } else if (!invoicedVal.isBlank()) {
                    // "Invoiced" text but no matching invoice — create one on the spot
                    LocalDate invoicedDate = SpreadsheetUtil.parseDate(invoicedVal);
                    BigDecimal invoiceAmount = charge.signum() > 0 ? charge : BigDecimal.ZERO;
                    matched = new Invoice();
                    matched.setReference(nextReference());
                    matched.setClient(client);
                    matched.setAmount(invoiceAmount);
                    matched.setAmountPaid(invoiceAmount);
                    matched.setCurrency(currency);
                    matched.setIssuedAt(invoicedDate != null ? invoicedDate : date);
                    matched.setStatus("paid");
                    matched = invoiceRepository.saveAndFlush(matched);

                    InvoiceItem onTheSpotItem = null;
                    if (invoiceAmount.signum() > 0) {
                        onTheSpotItem = new InvoiceItem();
                        onTheSpotItem.setInvoice(matched);
                        onTheSpotItem.setProject(project);
                        onTheSpotItem.setDescription(te.getDescription());
                        onTheSpotItem.setUnitPrice(invoiceAmount);
                        onTheSpotItem.setUnitCount(BigDecimal.ONE);
                        onTheSpotItem.setTotal(invoiceAmount);
                        onTheSpotItem = invoiceItemRepository.save(onTheSpotItem);

                        Payment payment = new Payment();
                        payment.setInvoice(matched);
                        payment.setPaidAt(invoicedDate != null ? invoicedDate : date);
                        payment.setAmount(invoiceAmount);
                        paymentRepository.save(payment);
                    }

                    invoiceLookup.put(client.getId() + ":" + (invoicedDate != null ? invoicedDate : date), matched);
                    te.setInvoice(matched);
                    if (onTheSpotItem != null) te.setInvoiceItem(onTheSpotItem);
                    te.setBilled(true);
                } else {
                    te.setBilled(false);
                }

                timeEntryRepository.save(te);
                count++;
            } catch (Exception e) {
                if (errors.size() < 50) errors.add("Time row " + (i + 2) + ": " + e.getMessage());
            }
        }
        return count;
    }

    // ── Step 4: Invoices + Payments ──

    private int[] importInvoicesAndPayments(ParsedFile sheet, Map<String, Client> clientByRaw,
                                            Map<String, Project> projectCache, List<String> errors) {
        if (sheet == null) return new int[]{0, 0};
        int invCount = 0, payCount = 0;

        for (int i = 0; i < sheet.rows().size(); i++) {
            var row = sheet.rows().get(i);
            try {
                Client client = clientByRaw.get(SpreadsheetUtil.val(row, "Client"));
                if (client == null) continue;

                BigDecimal amount = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Requested Amount"));
                LocalDate issuedAt = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Invoice Sent"));
                if (amount.signum() <= 0 || issuedAt == null) continue;

                String currency = SpreadsheetUtil.currencyFromSymbol(SpreadsheetUtil.val(row, "$"));
                String projectName = SpreadsheetUtil.val(row, "Project");

                String ref = SpreadsheetUtil.val(row, "Ref");
                String reference = (ref.isBlank() || ref.equalsIgnoreCase("N/A")) ? nextReference() : ref;

                // Skip if reference already exists (reimport safety)
                if (invoiceRepository.findAll().stream()
                        .anyMatch(existing -> reference.equals(existing.getReference()))) {
                    continue;
                }

                // Exchange rate from spreadsheet "USD to ILS" column, or 1.0 for USD
                BigDecimal exchangeRate = BigDecimal.ONE;
                if (!"USD".equals(currency)) {
                    exchangeRate = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "USD to ILS"));
                    if (exchangeRate.signum() <= 0) exchangeRate = BigDecimal.ONE;
                }

                Invoice inv = new Invoice();
                inv.setReference(reference);
                inv.setClient(client);
                inv.setAmount(amount);
                inv.setCurrency(currency);
                inv.setExchangeRate(exchangeRate);
                inv.setIssuedAt(issuedAt);
                inv.setSentAt(issuedAt.atStartOfDay()); // "Invoice Sent" date = both issued and sent
                inv.setDueAt(SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Payment Due Date")));
                inv.setNotes(SpreadsheetUtil.val(row, "Notes"));
                inv = invoiceRepository.saveAndFlush(inv);

                Project project = projectName.isBlank() ? null
                        : projectCache.get(client.getId() + ":" + projectName);

                InvoiceItem item = new InvoiceItem();
                item.setInvoice(inv);
                item.setProject(project);
                item.setDescription(projectName.isBlank() ? "Services" : projectName);
                item.setUnitPrice(amount);
                item.setUnitCount(BigDecimal.ONE);
                item.setTotal(amount);
                invoiceItemRepository.save(item);
                invCount++;

                // Payment — one record per invoice for the paid amount
                BigDecimal paid = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Amount Paid"));
                if (paid.signum() > 0) {
                    LocalDate paidAt = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Paid At"));
                    if (paidAt == null) paidAt = issuedAt; // fallback to issue date

                    Payment payment = new Payment();
                    payment.setInvoice(inv);
                    payment.setPaidAt(paidAt);
                    payment.setAmount(paid);
                    payment.setExchangeRate(exchangeRate);
                    paymentRepository.save(payment);

                    inv.setAmountPaid(paid);
                    inv.setStatus(paid.compareTo(amount) >= 0 ? "paid" : "unpaid");
                    payCount++;
                }

                // Mark overdue if not fully paid and past due
                if (!"paid".equals(inv.getStatus()) && inv.getDueAt() != null
                        && inv.getDueAt().isBefore(LocalDate.now())) {
                    inv.setStatus("overdue");
                }
            } catch (Exception e) {
                if (errors.size() < 50) errors.add("Invoice row " + (i + 2) + ": " + e.getMessage());
            }
        }
        return new int[]{invCount, payCount};
    }

    // ── Step 5: Subscriptions + generated monthly expenses ──

    private int[] importSubscriptionsAndExpenses(ParsedFile sheet, List<String> errors) {
        if (sheet == null) return new int[]{0, 0};
        int subCount = 0, expCount = 0;

        for (int i = 0; i < sheet.rows().size(); i++) {
            var row = sheet.rows().get(i);
            try {
                String vendor = SpreadsheetUtil.val(row, "Name");
                String description = SpreadsheetUtil.val(row, "Role");
                // Some rows have vendor in "Role" column and "Name" blank
                if (vendor.isBlank() && !description.isBlank()) {
                    vendor = description;
                    description = "";
                }
                BigDecimal cost = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Cost"));
                LocalDate from = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "From"));
                if (vendor.isBlank() || cost.signum() <= 0 || from == null) continue;

                LocalDate to = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "To"));

                Subscription sub = new Subscription();
                sub.setVendor(vendor);
                sub.setDescription(description);
                sub.setCategory("software");
                sub.setAmount(cost);
                sub.setCurrency("USD");
                sub.setCycle("monthly");
                sub.setStartedOn(from);
                if (to != null && to.isBefore(LocalDate.now())) sub.setCancelledOn(to);
                sub = subscriptionRepository.saveAndFlush(sub);
                subCount++;

                // Generate an expense for each month in the subscription period
                LocalDate end = (to != null && !to.isAfter(LocalDate.now())) ? to : LocalDate.now();
                LocalDate month = from;
                while (!month.isAfter(end)) {
                    Expense exp = new Expense();
                    exp.setVendor(vendor);
                    exp.setDescription(description);
                    exp.setCategory("software");
                    exp.setAmount(cost);
                    exp.setCurrency("USD");
                    exp.setIncurredOn(month);
                    exp.setSubscription(sub);
                    exp.setBillable(false);
                    exp.setBilled(false);
                    expenseRepository.saveAndFlush(exp);
                    expCount++;
                    month = month.plusMonths(1);
                }
            } catch (Exception e) {
                if (errors.size() < 50) errors.add("Subscription row " + (i + 2) + ": " + e.getMessage() + " [vendor=" + SpreadsheetUtil.val(row, "Name") + "]");
            }
        }
        return new int[]{subCount, expCount};
    }

    // ── Step 6: One-time expenses ──

    private int importOneTimeExpenses(ParsedFile sheet, List<String> errors) {
        if (sheet == null) return 0;
        int count = 0;
        for (int i = 0; i < sheet.rows().size(); i++) {
            var row = sheet.rows().get(i);
            try {
                BigDecimal cost = SpreadsheetUtil.parseBigDecimal(SpreadsheetUtil.val(row, "Cost"));
                LocalDate date = SpreadsheetUtil.parseDate(SpreadsheetUtil.val(row, "Date"));
                if (cost.signum() <= 0 || date == null) continue;

                Expense exp = new Expense();
                exp.setVendor(SpreadsheetUtil.val(row, "Name"));
                exp.setDescription(SpreadsheetUtil.val(row, "Role"));
                exp.setCategory("other");
                exp.setAmount(cost);
                exp.setCurrency("USD");
                exp.setIncurredOn(date);
                exp.setBillable(false);
                exp.setBilled(false);
                expenseRepository.save(exp);
                count++;
            } catch (Exception e) {
                if (errors.size() < 50) errors.add("Expense row " + (i + 2) + ": " + e.getMessage());
            }
        }
        return count;
    }

    // ── Backfill exchange rates ──

    @Transactional
    public int[] backfillExchangeRates() {
        int invoiceCount = 0;
        for (Invoice inv : invoiceRepository.findAll()) {
            if ("USD".equals(inv.getCurrency())) continue;
            if (inv.getExchangeRate() != null && inv.getExchangeRate().compareTo(BigDecimal.ONE) != 0) continue;
            if (inv.getIssuedAt() == null) continue;

            inv.setExchangeRate(exchangeRateService.getHistoricRate(inv.getCurrency(), inv.getIssuedAt()));
            invoiceCount++;
        }

        int paymentCount = 0;
        for (Payment p : paymentRepository.findAll()) {
            String currency = p.getInvoice().getCurrency();
            if ("USD".equals(currency)) continue;
            if (p.getExchangeRate() != null && p.getExchangeRate().compareTo(BigDecimal.ONE) != 0) continue;
            if (p.getPaidAt() == null) continue;

            p.setExchangeRate(exchangeRateService.getHistoricRate(currency, p.getPaidAt()));
            paymentCount++;
        }

        int expenseCount = 0;
        for (dev.dynamiq.talli.model.Expense ex : expenseRepository.findAll()) {
            if ("USD".equals(ex.getCurrency())) continue;
            if (ex.getExchangeRate() != null && ex.getExchangeRate().compareTo(BigDecimal.ONE) != 0) continue;
            if (ex.getIncurredOn() == null) continue;

            ex.setExchangeRate(exchangeRateService.getHistoricRate(ex.getCurrency(), ex.getIncurredOn()));
            expenseCount++;
        }

        return new int[]{invoiceCount, paymentCount, expenseCount};
    }
}
