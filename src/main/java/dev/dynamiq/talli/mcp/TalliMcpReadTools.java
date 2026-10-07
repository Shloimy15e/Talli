package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.InvoiceItemRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.PaymentRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.ReportService;
import dev.dynamiq.talli.service.EmailThreadService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
@Transactional(readOnly = true)
public class TalliMcpReadTools {

    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final TimeEntryRepository timeEntries;
    private final ExpenseRepository expenses;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository invoiceItems;
    private final PaymentRepository payments;
    private final SubscriptionRepository subscriptions;
    private final ReportService reports;
    private final EmailRepository emails;
    private final EmailThreadService threads;

    public TalliMcpReadTools(ClientRepository clients, ProjectRepository projects,
                             TimeEntryRepository timeEntries, ExpenseRepository expenses,
                              InvoiceRepository invoices, InvoiceItemRepository invoiceItems,
                              PaymentRepository payments, SubscriptionRepository subscriptions,
                              ReportService reports, EmailRepository emails,
                              EmailThreadService threads) {
        this.clients = clients;
        this.projects = projects;
        this.timeEntries = timeEntries;
        this.expenses = expenses;
        this.invoices = invoices;
        this.invoiceItems = invoiceItems;
        this.payments = payments;
        this.subscriptions = subscriptions;
        this.reports = reports;
        this.emails = emails;
        this.threads = threads;
    }

    @McpTool(name = "find_clients", title = "Find clients",
            description = "Search clients by name, email, phone, notes, or ID, or omit query to list all clients. Returns contact details, billing address, payment terms, and notes, sorted by name. Each page contains at most 100 records; increase offset to continue.",
            annotations = @McpTool.McpAnnotations(title = "Find clients", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('view-clients')")
    public List<McpViews.ClientView> findClients(
            @McpToolParam(description = "Optional text search or exact numeric ID", required = false) String query,
            @McpToolParam(description = "Records to skip for pagination; defaults to 0", required = false) Integer offset,
            @McpToolParam(description = "Maximum records, 1-100; defaults to 50", required = false) Integer limit) {
        String term = normalized(query);
        return clients.findAll().stream()
                .filter(c -> term == null || matchesClient(c, term))
                .sorted(Comparator.comparing(Client::getName, String.CASE_INSENSITIVE_ORDER))
                .skip(offset(offset))
                .limit(limit(limit))
                .map(McpViews::client)
                .toList();
    }

    @McpTool(name = "find_client_emails", title = "Find client emails",
            description = "List recorded incoming and outgoing client emails, newest first, with subject, body excerpt, sender/recipient, source, initiating account, provider ID, send status, errors, bounce reason, and delivery-event timestamps when known. Returns nextOffset for another page. Use get_email_conversation for full plain-text and HTML bodies and the local thread before replying. Requires an admin account.",
            annotations = @McpTool.McpAnnotations(title = "Find client emails", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasRole('admin')")
    public EmailSearchResult findClientEmails(
            @McpToolParam(description = "Talli client ID", required = true) Long clientId,
            @McpToolParam(description = "Messages to skip; defaults to 0", required = false) Integer offset,
            @McpToolParam(description = "Maximum messages to return, 1-100; defaults to 50", required = false) Integer limit) {
        if (clientId == null) throw new IllegalArgumentException("client_id is required");
        long start = offset(offset);
        int size = (int) limit(limit);
        List<Email> page = emails.findClientEmails(clientId, size + 1, start);
        boolean hasMore = page.size() > size;
        return new EmailSearchResult(page.stream().limit(size).map(McpViews::emailSummary).toList(),
                start, hasMore ? start + size : null);
    }

    @McpTool(name = "get_email_conversation", title = "Get an email conversation",
            description = "Read full plain-text and HTML bodies of locally recorded incoming and outgoing messages in an email conversation, oldest first, with threading headers, source, initiating account, provider ID, send status, errors, bounce reason, and delivery-event timestamps when known. Returns nextOffset for another page; an email without saved threading metadata returns only itself. Requires an admin account.",
            annotations = @McpTool.McpAnnotations(title = "Get an email conversation", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasRole('admin')")
    public EmailConversation getEmailConversation(
            @McpToolParam(description = "Any email ID in the conversation", required = true) Long emailId,
            @McpToolParam(description = "Messages to skip; defaults to 0", required = false) Integer offset,
            @McpToolParam(description = "Maximum messages to return, 1-100; defaults to 50", required = false) Integer limit) {
        if (emailId == null) throw new IllegalArgumentException("email_id is required");
        Email email = emails.findById(emailId)
                .orElseThrow(() -> new IllegalArgumentException("Email not found: " + emailId));
        long start = offset(offset);
        int size = (int) limit(limit);
        List<Email> conversation = threads.conversation(email);
        boolean hasMore = conversation.size() > start + size;
        List<McpViews.EmailView> messages = conversation.stream().skip(start).limit(size).map(McpViews::email).toList();
        Long rootId = email.getThreadRootId() == null ? email.getId() : email.getThreadRootId();
        return new EmailConversation(rootId, messages, hasMore ? start + size : null);
    }

    @McpTool(name = "find_projects", title = "Find projects",
            description = "Search projects by project/client name with optional clientId and status filters, or omit filters to list all projects. Returns client, rate type and amount, currency, billing frequency, status, billable state, and notes. Each name-sorted page contains at most 100 records; increase offset to continue.",
            annotations = @McpTool.McpAnnotations(title = "Find projects", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('view-projects')")
    public List<McpViews.ProjectView> findProjects(
            @McpToolParam(description = "Optional project or client name search", required = false) String query,
            @McpToolParam(description = "Optional client ID", required = false) Long clientId,
            @McpToolParam(description = "Optional status: active, paused, completed, or cancelled", required = false) String status,
            @McpToolParam(description = "Records to skip for pagination; defaults to 0", required = false) Integer offset,
            @McpToolParam(description = "Maximum records, 1-100; defaults to 50", required = false) Integer limit) {
        String term = normalized(query);
        String wantedStatus = normalized(status);
        return projects.findAll().stream()
                .filter(p -> clientId == null || p.getClient().getId().equals(clientId))
                .filter(p -> wantedStatus == null || wantedStatus.equalsIgnoreCase(p.getStatus()))
                .filter(p -> term == null || contains(p.getName(), term) || contains(p.getClient().getName(), term))
                .sorted(Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
                .skip(offset(offset))
                .limit(limit(limit))
                .map(McpViews::project)
                .toList();
    }

    @McpTool(name = "find_time_entries", title = "Find time entries",
            description = "List completed time entries and running timers with client/project, start/end times, durationMinutes, running state, work description, billable/billed states, and invoiceId. Combine optional clientId, projectId, inclusive start-date range, billable, and billed filters; omit filters to list all. Newest first; use offset and limit to page through results.",
            annotations = @McpTool.McpAnnotations(title = "Find time entries", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('view-time')")
    public List<McpViews.TimeEntryView> findTimeEntries(
            @McpToolParam(description = "Optional client ID", required = false) Long clientId,
            @McpToolParam(description = "Optional project ID", required = false) Long projectId,
            @McpToolParam(description = "Optional inclusive start date, YYYY-MM-DD", required = false) String from,
            @McpToolParam(description = "Optional inclusive end date, YYYY-MM-DD", required = false) String to,
            @McpToolParam(description = "Optional billable filter", required = false) Boolean billable,
            @McpToolParam(description = "Optional billed filter", required = false) Boolean billed,
            @McpToolParam(description = "Records to skip for pagination; defaults to 0", required = false) Integer offset,
            @McpToolParam(description = "Maximum records, 1-100; defaults to 50", required = false) Integer limit) {
        DateRange range = range(from, to);
        return timeEntries.findAllByOrderByStartedAtDesc().stream()
                .filter(e -> projectId == null || e.getProject().getId().equals(projectId))
                .filter(e -> clientId == null || e.getProject().getClient().getId().equals(clientId))
                .filter(e -> inRange(e, range))
                .filter(e -> billable == null || billable.equals(e.getBillable()))
                .filter(e -> billed == null || billed.equals(e.getBilled()))
                .skip(offset(offset))
                .limit(limit(limit))
                .map(McpViews::timeEntry)
                .toList();
    }

    @McpTool(name = "current_timer", title = "Get the current timer",
            description = "Check whether a Talli timer is running and return it when present.",
            annotations = @McpTool.McpAnnotations(title = "Get the current timer", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('view-time')")
    public TimerStatus currentTimer() {
        return timeEntries.findFirstByEndedAtIsNullOrderByStartedAtDesc()
                .map(entry -> new TimerStatus(true, McpViews.timeEntry(entry)))
                .orElseGet(() -> new TimerStatus(false, null));
    }

    @McpTool(name = "find_expenses", title = "Find expenses",
            description = "List expenses with amount/currency, category, vendor, description, payment method, receipt URL, client/project/subscription links, billable/billed states, and invoiceId. Combine optional association, inclusive incurred-date range, category, and billing filters; omit filters to list all. Newest first; use offset and limit to page through results.",
            annotations = @McpTool.McpAnnotations(title = "Find expenses", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('view-expenses')")
    public List<McpViews.ExpenseView> findExpenses(
            @McpToolParam(description = "Optional client ID", required = false) Long clientId,
            @McpToolParam(description = "Optional project ID", required = false) Long projectId,
            @McpToolParam(description = "Optional subscription ID", required = false) Long subscriptionId,
            @McpToolParam(description = "Optional filter for expenses linked or not linked to any subscription", required = false) Boolean linkedToSubscription,
            @McpToolParam(description = "Optional inclusive start date, YYYY-MM-DD", required = false) String from,
            @McpToolParam(description = "Optional inclusive end date, YYYY-MM-DD", required = false) String to,
            @McpToolParam(description = "Optional category", required = false) String category,
            @McpToolParam(description = "Optional billable filter", required = false) Boolean billable,
            @McpToolParam(description = "Optional billed filter", required = false) Boolean billed,
            @McpToolParam(description = "Records to skip for pagination; defaults to 0", required = false) Integer offset,
            @McpToolParam(description = "Maximum records, 1-100; defaults to 50", required = false) Integer limit) {
        DateRange range = range(from, to);
        String wantedCategory = normalized(category);
        return expenses.findAllByOrderByIncurredOnDesc().stream()
                .filter(e -> clientId == null || e.getClient() != null && e.getClient().getId().equals(clientId))
                .filter(e -> projectId == null || e.getProject() != null && e.getProject().getId().equals(projectId))
                .filter(e -> subscriptionId == null || e.getSubscription() != null
                        && e.getSubscription().getId().equals(subscriptionId))
                .filter(e -> linkedToSubscription == null
                        || linkedToSubscription == (e.getSubscription() != null))
                .filter(e -> inRange(e.getIncurredOn(), range))
                .filter(e -> wantedCategory == null || wantedCategory.equalsIgnoreCase(e.getCategory()))
                .filter(e -> billable == null || billable.equals(e.getBillable()))
                .filter(e -> billed == null || billed.equals(e.getBilled()))
                .skip(offset(offset))
                .limit(limit(limit))
                .map(McpViews::expense)
                .toList();
    }

    @McpTool(name = "find_invoices", title = "Find invoices",
            description = "List invoices with reference, client, amount, amountPaid, outstanding balance, currency, status, notes, billing period, and issue/due/payment dates. Combine optional clientId, status, and inclusive issued-date range filters; omit filters to list all. Newest first; use offset and limit to continue. Use get_invoice for line items and payment records, record_payment/delete_payment to manage payments, and set_invoice_ach_link to manage the ACH link.",
            annotations = @McpTool.McpAnnotations(title = "Find invoices", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('view-invoices')")
    public List<McpViews.InvoiceView> findInvoices(
            @McpToolParam(description = "Optional client ID", required = false) Long clientId,
            @McpToolParam(description = "Optional invoice status", required = false) String status,
            @McpToolParam(description = "Optional inclusive start date, YYYY-MM-DD", required = false) String from,
            @McpToolParam(description = "Optional inclusive end date, YYYY-MM-DD", required = false) String to,
            @McpToolParam(description = "Records to skip for pagination; defaults to 0", required = false) Integer offset,
            @McpToolParam(description = "Maximum records, 1-100; defaults to 50", required = false) Integer limit) {
        DateRange range = range(from, to);
        String wantedStatus = normalized(status);
        return invoices.findAllByOrderByIssuedAtDescIdDesc().stream()
                .filter(i -> clientId == null || i.getClient().getId().equals(clientId))
                .filter(i -> wantedStatus == null || wantedStatus.equalsIgnoreCase(i.getStatus()))
                .filter(i -> inRange(i.getIssuedAt(), range))
                .skip(offset(offset))
                .limit(limit(limit))
                .map(McpViews::invoice)
                .toList();
    }

    @McpTool(name = "get_invoice", title = "Get invoice details",
            description = "Get one invoice's totals, paid amount, outstanding balance, status, and dates together with all line items and payment records. Line items include project, description, unit price/count, and total; payments include id, date, amount, method, reference, notes, source, and external provider/transaction IDs. Use a payment's id as paymentId in delete_payment.",
            annotations = @McpTool.McpAnnotations(title = "Get invoice details", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('view-invoices') and hasAuthority('view-payments')")
    public InvoiceDetails getInvoice(
            @McpToolParam(description = "Invoice ID", required = true) Long invoiceId) {
        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));
        return new InvoiceDetails(McpViews.invoice(invoice),
                invoiceItems.findByInvoiceIdOrderByIdAsc(invoiceId).stream().map(McpViews::invoiceItem).toList(),
                payments.findByInvoiceIdOrderByPaidAtDescIdDesc(invoiceId).stream().map(McpViews::payment).toList());
    }

    @McpTool(name = "find_subscriptions", title = "Find subscriptions",
            description = "List recurring-expense subscriptions with vendor, amount/currency, category, monthly/yearly cycle, start/cancellation/next-due dates, client/project links, payment method, management/cancellation URLs, and active state. Omit active to include both active and cancelled subscriptions. Vendor-sorted; use offset and limit to continue. Manage templates with create_subscription/update_subscription and lifecycle tools; use find_expenses with subscriptionId to read recorded charges.",
            annotations = @McpTool.McpAnnotations(title = "Find subscriptions", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('view-expenses')")
    public List<McpViews.SubscriptionView> findSubscriptions(
            @McpToolParam(description = "Optional client ID", required = false) Long clientId,
            @McpToolParam(description = "Optional project ID", required = false) Long projectId,
            @McpToolParam(description = "Optional active-state filter", required = false) Boolean active,
            @McpToolParam(description = "Records to skip for pagination; defaults to 0", required = false) Integer offset,
            @McpToolParam(description = "Maximum records, 1-100; defaults to 50", required = false) Integer limit) {
        return subscriptions.findAllByOrderByVendorAsc().stream()
                .filter(s -> clientId == null || s.getClient() != null && s.getClient().getId().equals(clientId))
                .filter(s -> projectId == null || s.getProject() != null && s.getProject().getId().equals(projectId))
                .filter(s -> active == null || active == s.isActive())
                .skip(offset(offset))
                .limit(limit(limit))
                .map(McpViews::subscription)
                .toList();
    }

    @McpTool(name = "run_report", title = "Run a Talli report",
            description = "Run financial reports with reportType: financial_trend (monthly, quarterly, and yearly invoiced/received/expense totals), client_profit_loss, time_utilization, accounts_receivable_aging, revenue_by_project, expenses_by_category, payment_history, or outstanding_invoices. financial_trend uses months; aging and outstanding invoices show the current position. Other reports use the inclusive from/to date range. Returns reportType, date range, and report data.",
            annotations = @McpTool.McpAnnotations(title = "Run a Talli report", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('view-reports')")
    public ReportResult runReport(
            @McpToolParam(description = "Report type listed in the tool description", required = true) String reportType,
            @McpToolParam(description = "Optional inclusive start date, YYYY-MM-DD; defaults to one year ago", required = false) String from,
            @McpToolParam(description = "Optional inclusive end date, YYYY-MM-DD; defaults to today", required = false) String to,
            @McpToolParam(description = "Months for financial_trend, 1-120; defaults to 12", required = false) Integer months) {
        String type = requireText(reportType, "report_type").toLowerCase(Locale.ROOT);
        if ("financial_trend".equals(type)) {
            int count = months == null ? 12 : Math.max(1, Math.min(months, 120));
            var trend = new FinancialTrend(reports.monthlyRevenue(count),
                    reports.quarterlyRevenue(count), reports.yearlyRevenue(count));
            return new ReportResult(type, null, null, trend);
        }

        LocalDate end = to == null || to.isBlank() ? LocalDate.now() : parseDate(to, "to");
        LocalDate start = from == null || from.isBlank() ? end.minusYears(1).plusDays(1) : parseDate(from, "from");
        if (start.isAfter(end)) throw new IllegalArgumentException("from must be on or before to");

        Object data = switch (type) {
            case "client_profit_loss" -> reports.clientProfitLoss(start, end);
            case "time_utilization" -> reports.timeUtilization(start, end);
            case "accounts_receivable_aging" -> reports.accountsReceivableAging();
            case "revenue_by_project" -> reports.revenueByProject(start, end);
            case "expenses_by_category" -> reports.expensesByCategory(start, end);
            case "payment_history" -> reports.paymentHistory(start, end);
            case "outstanding_invoices" -> reports.outstandingInvoices();
            default -> throw new IllegalArgumentException("Unknown report_type: " + reportType);
        };
        return new ReportResult(type, start, end, data);
    }

    private static boolean matchesClient(Client client, String term) {
        if (client.getId() != null && client.getId().toString().equals(term)) return true;
        return contains(client.getName(), term) || contains(client.getEmail(), term)
                || contains(client.getPhone(), term) || contains(client.getNotes(), term);
    }

    private static boolean inRange(TimeEntry entry, DateRange range) {
        return entry.getStartedAt() != null && inRange(entry.getStartedAt().toLocalDate(), range);
    }

    private static boolean inRange(LocalDate date, DateRange range) {
        if (date == null) return false;
        return (range.from() == null || !date.isBefore(range.from()))
                && (range.to() == null || !date.isAfter(range.to()));
    }

    private static DateRange range(String from, String to) {
        LocalDate start = from == null || from.isBlank() ? null : parseDate(from, "from");
        LocalDate end = to == null || to.isBlank() ? null : parseDate(to, "to");
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("from must be on or before to");
        }
        return new DateRange(start, end);
    }

    private static LocalDate parseDate(String value, String name) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(name + " must be an ISO date (YYYY-MM-DD)");
        }
    }

    private static long limit(Integer requested) {
        return requested == null ? 50 : Math.max(1, Math.min(requested, 100));
    }

    private static long offset(Integer requested) {
        return requested == null ? 0 : Math.max(0, requested);
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private record DateRange(LocalDate from, LocalDate to) {}

    public record EmailSearchResult(List<McpViews.EmailView> emails, long offset, Long nextOffset) {}

    public record EmailConversation(Long threadRootId, List<McpViews.EmailView> messages, Long nextOffset) {}

    public record InvoiceDetails(McpViews.InvoiceView invoice,
                                 List<McpViews.InvoiceItemView> items,
                                 List<McpViews.PaymentView> payments) {}

    public record FinancialTrend(List<ReportService.MonthSummary> monthly,
                                 List<ReportService.QuarterSummary> quarterly,
                                 List<ReportService.YearSummary> yearly) {}

    public record ReportResult(String reportType, LocalDate from, LocalDate to, Object data) {}

    public record TimerStatus(boolean running, McpViews.TimeEntryView entry) {}
}
