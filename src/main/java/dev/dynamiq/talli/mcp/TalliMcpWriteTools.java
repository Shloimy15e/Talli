package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.AgentEmailService;
import dev.dynamiq.talli.service.ExpenseService;
import dev.dynamiq.talli.service.InvoiceService;
import dev.dynamiq.talli.service.PaymentService;
import dev.dynamiq.talli.service.ProjectService;
import dev.dynamiq.talli.service.TimeEntryService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Component
public class TalliMcpWriteTools {

    private static final Set<String> RATE_TYPES = Set.of("hourly", "fixed", "retainer");
    private static final Set<String> PROJECT_STATUSES = Set.of("active", "paused", "completed", "cancelled");

    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final TimeEntryRepository timeEntries;
    private final TimeEntryService timeEntryService;
    private final ExpenseService expenseService;
    private final ProjectService projectService;
    private final InvoiceRepository invoices;
    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final AgentEmailService agentEmailService;

    public TalliMcpWriteTools(ClientRepository clients, ProjectRepository projects,
                              TimeEntryRepository timeEntries, TimeEntryService timeEntryService,
                              ExpenseService expenseService, ProjectService projectService,
                              InvoiceRepository invoices, PaymentService paymentService,
                              InvoiceService invoiceService, AgentEmailService agentEmailService) {
        this.clients = clients;
        this.projects = projects;
        this.timeEntries = timeEntries;
        this.timeEntryService = timeEntryService;
        this.expenseService = expenseService;
        this.projectService = projectService;
        this.invoices = invoices;
        this.paymentService = paymentService;
        this.invoiceService = invoiceService;
        this.agentEmailService = agentEmailService;
    }

    @McpTool(name = "create_client", title = "Create a client",
            description = "Create a Talli client. This does not send email or create billing records.",
            annotations = @McpTool.McpAnnotations(title = "Create a client", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-clients')")
    @Transactional
    public McpViews.ClientView createClient(
            @McpToolParam(description = "Client name", required = true) String name,
            @McpToolParam(description = "Optional billing/contact email", required = false) String email,
            @McpToolParam(description = "Optional phone number", required = false) String phone,
            @McpToolParam(description = "Optional billing address", required = false) String billingAddress,
            @McpToolParam(description = "Payment terms in days; defaults to 30", required = false) Integer paymentTermsDays,
            @McpToolParam(description = "Optional internal notes", required = false) String notes) {
        String clientName = requireText(name, "name");
        clients.findByNameIgnoreCase(clientName).ifPresent(existing -> {
            throw new IllegalArgumentException("Client already exists with ID " + existing.getId());
        });
        int terms = paymentTermsDays == null ? 30 : paymentTermsDays;
        if (terms < 0 || terms > 365) {
            throw new IllegalArgumentException("payment_terms_days must be between 0 and 365");
        }

        Client client = new Client();
        client.setName(clientName);
        client.setEmail(emptyToNull(email));
        client.setPhone(emptyToNull(phone));
        client.setBillingAddress(emptyToNull(billingAddress));
        client.setPaymentTermsDays(terms);
        client.setNotes(emptyToNull(notes));
        return McpViews.client(clients.save(client));
    }

    @McpTool(name = "update_client", title = "Update a client",
            description = "Patch a Talli client. Omitted values stay unchanged; blank optional text clears that field.",
            annotations = @McpTool.McpAnnotations(title = "Update a client", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-clients')")
    @Transactional
    public McpViews.ClientView updateClient(
            @McpToolParam(description = "Existing client ID", required = true) Long clientId,
            @McpToolParam(description = "Optional new name", required = false) String name,
            @McpToolParam(description = "Optional email; blank clears it", required = false) String email,
            @McpToolParam(description = "Optional phone; blank clears it", required = false) String phone,
            @McpToolParam(description = "Optional billing address; blank clears it", required = false) String billingAddress,
            @McpToolParam(description = "Optional tax ID; blank clears it", required = false) String taxId,
            @McpToolParam(description = "Optional internal notes; blank clears them", required = false) String notes,
            @McpToolParam(description = "Optional payment terms in days, 0-365", required = false) Integer paymentTermsDays) {
        if (clientId == null) throw new IllegalArgumentException("client_id is required");
        Client client = clients.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        if (name != null) {
            String clientName = requireText(name, "name");
            clients.findByNameIgnoreCase(clientName)
                    .filter(existing -> !existing.getId().equals(clientId))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Client name belongs to ID " + existing.getId());
                    });
            client.setName(clientName);
        }
        if (email != null) client.setEmail(emptyToNull(email));
        if (phone != null) client.setPhone(emptyToNull(phone));
        if (billingAddress != null) client.setBillingAddress(emptyToNull(billingAddress));
        if (taxId != null) client.setTaxId(emptyToNull(taxId));
        if (notes != null) client.setNotes(emptyToNull(notes));
        if (paymentTermsDays != null) {
            if (paymentTermsDays < 0 || paymentTermsDays > 365) {
                throw new IllegalArgumentException("payment_terms_days must be between 0 and 365");
            }
            client.setPaymentTermsDays(paymentTermsDays);
        }
        return McpViews.client(clients.save(client));
    }

    @McpTool(name = "create_project", title = "Create a project",
            description = "Create an active project for an existing client. rate_type must be hourly, fixed, or retainer.",
            annotations = @McpTool.McpAnnotations(title = "Create a project", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-projects')")
    @Transactional
    public McpViews.ProjectView createProject(
            @McpToolParam(description = "Project name", required = true) String name,
            @McpToolParam(description = "Existing client ID", required = true) Long clientId,
            @McpToolParam(description = "hourly, fixed, or retainer", required = true) String rateType,
            @McpToolParam(description = "Hourly rate, fixed contract amount, or monthly retainer amount", required = true) BigDecimal currentRate,
            @McpToolParam(description = "Three-letter currency code; defaults to USD", required = false) String currency,
            @McpToolParam(description = "Optional billing frequency", required = false) String billingFrequency,
            @McpToolParam(description = "Whether work is billable; defaults to true", required = false) Boolean billable,
            @McpToolParam(description = "Optional internal notes", required = false) String notes) {
        String projectName = requireText(name, "name");
        if (clientId == null) throw new IllegalArgumentException("client_id is required");
        Client client = clients.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        if (projects.findByClientId(clientId).stream().anyMatch(p -> p.getName().equalsIgnoreCase(projectName))) {
            throw new IllegalArgumentException("A project with this name already exists for the client");
        }
        String type = normalizedCode(rateType, "rate_type");
        if (!RATE_TYPES.contains(type)) {
            throw new IllegalArgumentException("rate_type must be hourly, fixed, or retainer");
        }
        if (currentRate == null || currentRate.signum() < 0) {
            throw new IllegalArgumentException("current_rate must be zero or greater");
        }

        Project project = new Project();
        project.setName(projectName);
        project.setClient(client);
        project.setRateType(type);
        project.setCurrentRate(currentRate);
        project.setCurrency(currency(currency));
        project.setBillingFrequency(emptyToNull(billingFrequency));
        project.setBillable(billable == null || billable);
        project.setStatus("active");
        project.setNotes(emptyToNull(notes));
        return McpViews.project(projects.save(project));
    }

    @McpTool(name = "update_project", title = "Update a project",
            description = "Patch a project without moving it to another client. Omitted values stay unchanged; blank optional text clears it. Changing current_rate requires rate_change_reason and records the change in project notes.",
            annotations = @McpTool.McpAnnotations(title = "Update a project", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-projects')")
    @Transactional
    public McpViews.ProjectView updateProject(
            @McpToolParam(description = "Existing project ID", required = true) Long projectId,
            @McpToolParam(description = "Optional new name", required = false) String name,
            @McpToolParam(description = "Optional rate type: hourly, fixed, or retainer", required = false) String rateType,
            @McpToolParam(description = "Optional new rate or contract amount", required = false) BigDecimal currentRate,
            @McpToolParam(description = "Reason required when current_rate changes", required = false) String rateChangeReason,
            @McpToolParam(description = "Optional three-letter currency code", required = false) String currency,
            @McpToolParam(description = "Optional billing frequency; blank clears it", required = false) String billingFrequency,
            @McpToolParam(description = "Optional status: active, paused, completed, or cancelled", required = false) String status,
            @McpToolParam(description = "Optional billable state", required = false) Boolean billable,
            @McpToolParam(description = "Optional internal notes; blank clears them", required = false) String notes) {
        if (projectId == null) throw new IllegalArgumentException("project_id is required");
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (name != null) {
            String projectName = requireText(name, "name");
            boolean duplicate = projects.findByClientId(project.getClient().getId()).stream()
                    .anyMatch(existing -> !existing.getId().equals(projectId)
                            && existing.getName().equalsIgnoreCase(projectName));
            if (duplicate) throw new IllegalArgumentException("A project with this name already exists for the client");
            project.setName(projectName);
        }
        if (rateType != null) {
            String type = normalizedCode(rateType, "rate_type");
            if (!RATE_TYPES.contains(type)) {
                throw new IllegalArgumentException("rate_type must be hourly, fixed, or retainer");
            }
            project.setRateType(type);
        }
        if (currency != null) project.setCurrency(currency(currency));
        if (billingFrequency != null) project.setBillingFrequency(emptyToNull(billingFrequency));
        if (status != null) {
            String projectStatus = normalizedCode(status, "status");
            if (!PROJECT_STATUSES.contains(projectStatus)) {
                throw new IllegalArgumentException("status must be active, paused, completed, or cancelled");
            }
            project.setStatus(projectStatus);
        }
        if (billable != null) project.setBillable(billable);
        if (notes != null) project.setNotes(emptyToNull(notes));

        if (currentRate != null && currentRate.compareTo(project.getCurrentRate()) != 0) {
            if (currentRate.signum() < 0) throw new IllegalArgumentException("current_rate must be zero or greater");
            projectService.changeContractAmount(projectId, currentRate,
                    requireText(rateChangeReason, "rate_change_reason"));
        }
        return McpViews.project(projects.save(project));
    }

    @McpTool(name = "log_time", title = "Log completed time",
            description = "Log completed work. duration_minutes is required. If started_at is omitted, the entry ends now; otherwise use an ISO local date-time such as 2026-08-13T09:00:00.",
            annotations = @McpTool.McpAnnotations(title = "Log completed time", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-time')")
    public McpViews.TimeEntryView logTime(
            @McpToolParam(description = "Existing project ID", required = true) Long projectId,
            @McpToolParam(description = "Completed duration in minutes, 1-10080", required = true) Integer durationMinutes,
            @McpToolParam(description = "Optional ISO local start date-time; defaults to now minus the duration", required = false) String startedAt,
            @McpToolParam(description = "Description of the work", required = false) String description,
            @McpToolParam(description = "Whether the time is billable; defaults to true and is forced false for non-billable projects", required = false) Boolean billable) {
        if (projectId == null) throw new IllegalArgumentException("project_id is required");
        if (durationMinutes == null || durationMinutes < 1 || durationMinutes > 10_080) {
            throw new IllegalArgumentException("duration_minutes must be between 1 and 10080");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = startedAt == null || startedAt.isBlank()
                ? now.minusMinutes(durationMinutes)
                : parseDateTime(startedAt);
        LocalDateTime end = start.plusMinutes(durationMinutes);
        if (end.isAfter(now.plusMinutes(1))) {
            throw new IllegalArgumentException("Logged time cannot end in the future");
        }

        TimeEntry entry = timeEntryService.create(projectId, start, end,
                emptyToNull(description), billable == null || billable);
        return McpViews.timeEntry(entry);
    }

    @McpTool(name = "start_timer", title = "Start a timer",
            description = "Start a Talli timer for a project. Fails if another timer is already running.",
            annotations = @McpTool.McpAnnotations(title = "Start a timer", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-time')")
    public McpViews.TimeEntryView startTimer(
            @McpToolParam(description = "Existing project ID", required = true) Long projectId,
            @McpToolParam(description = "Optional work description", required = false) String description) {
        if (projectId == null) throw new IllegalArgumentException("project_id is required");
        timeEntries.findFirstByEndedAtIsNullOrderByStartedAtDesc().ifPresent(running -> {
            throw new IllegalStateException("Timer " + running.getId() + " is already running");
        });
        return McpViews.timeEntry(timeEntryService.startTimer(projectId, emptyToNull(description)));
    }

    @McpTool(name = "stop_timer", title = "Stop a timer",
            description = "Stop a running timer by ID. If timer_id is omitted, stops the current running timer.",
            annotations = @McpTool.McpAnnotations(title = "Stop a timer", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-time')")
    public McpViews.TimeEntryView stopTimer(
            @McpToolParam(description = "Optional timer ID; omit to stop the current timer", required = false) Long timerId) {
        Long id = timerId != null ? timerId : timeEntries.findFirstByEndedAtIsNullOrderByStartedAtDesc()
                .map(TimeEntry::getId)
                .orElseThrow(() -> new IllegalStateException("No timer is running"));
        return McpViews.timeEntry(timeEntryService.endTimer(id));
    }

    @McpTool(name = "log_expense", title = "Log an expense",
            description = "Log an expense in Talli. If a project is supplied, its client is used and any supplied client_id must match.",
            annotations = @McpTool.McpAnnotations(title = "Log an expense", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-expenses')")
    @Transactional
    public McpViews.ExpenseView logExpense(
            @McpToolParam(description = "Positive expense amount", required = true) BigDecimal amount,
            @McpToolParam(description = "Category: software, hardware, travel, meals, contractors, office, marketing, taxes, or other", required = true) String category,
            @McpToolParam(description = "Optional incurred date, YYYY-MM-DD; defaults to today", required = false) String incurredOn,
            @McpToolParam(description = "Three-letter currency code; defaults to USD", required = false) String currency,
            @McpToolParam(description = "Optional client ID", required = false) Long clientId,
            @McpToolParam(description = "Optional project ID", required = false) Long projectId,
            @McpToolParam(description = "Optional vendor", required = false) String vendor,
            @McpToolParam(description = "Optional description", required = false) String description,
            @McpToolParam(description = "Optional payment method", required = false) String paymentMethod,
            @McpToolParam(description = "Optional receipt URL", required = false) String receiptUrl,
            @McpToolParam(description = "Whether this is billable to the client; defaults to false", required = false) Boolean billable) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        String expenseCategory = normalizedCode(category, "category");
        if (!Expense.CATEGORIES.contains(expenseCategory)) {
            throw new IllegalArgumentException("Unknown category: " + category);
        }

        Project project = projectId == null ? null : projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        Client client = clientId == null ? null : clients.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        if (project != null) {
            if (client != null && !project.getClient().getId().equals(client.getId())) {
                throw new IllegalArgumentException("project_id does not belong to client_id");
            }
            client = project.getClient();
        }

        Expense expense = new Expense();
        expense.setAmount(amount);
        expense.setCategory(expenseCategory);
        expense.setIncurredOn(incurredOn == null || incurredOn.isBlank()
                ? LocalDate.now() : parseDate(incurredOn, "incurred_on"));
        expense.setCurrency(currency(currency));
        expense.setClient(client);
        expense.setProject(project);
        expense.setVendor(emptyToNull(vendor));
        expense.setDescription(emptyToNull(description));
        expense.setPaymentMethod(emptyToNull(paymentMethod));
        expense.setReceiptUrl(emptyToNull(receiptUrl));
        expense.setBillable(Boolean.TRUE.equals(billable));
        return McpViews.expense(expenseService.create(expense));
    }

    @McpTool(name = "record_payment", title = "Record an invoice payment",
            description = "Record a settled payment from any bank or payment provider. provider plus transaction_id is the idempotency key, so repeat calls do not duplicate a payment.",
            annotations = @McpTool.McpAnnotations(title = "Record an invoice payment", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-payments')")
    public McpViews.PaymentView recordPayment(
            @McpToolParam(description = "Talli invoice ID receiving the payment", required = true) Long invoiceId,
            @McpToolParam(description = "Positive amount in the invoice currency", required = true) BigDecimal amount,
            @McpToolParam(description = "Three-letter currency code; must match the invoice", required = true) String currency,
            @McpToolParam(description = "Settlement date, YYYY-MM-DD; defaults to today", required = false) String paidAt,
            @McpToolParam(description = "Stable provider slug such as mercury or chase", required = true) String provider,
            @McpToolParam(description = "Provider's stable transaction ID", required = true) String transactionId,
            @McpToolParam(description = "Optional payment method such as ACH, wire, check, card, or cash", required = false) String method,
            @McpToolParam(description = "Optional visible bank reference; defaults to transaction_id", required = false) String reference,
            @McpToolParam(description = "Optional internal notes", required = false) String notes) {
        if (invoiceId == null) throw new IllegalArgumentException("invoice_id is required");
        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));
        if ("void".equals(invoice.getStatus()) || "written_off".equals(invoice.getStatus())) {
            throw new IllegalStateException("Payments cannot be recorded against a " + invoice.getStatus() + " invoice");
        }

        String paymentCurrency = currency(currency);
        if (!paymentCurrency.equalsIgnoreCase(invoice.getCurrency())) {
            throw new IllegalArgumentException("currency must match the invoice currency " + invoice.getCurrency());
        }
        LocalDate paymentDate = paidAt == null || paidAt.isBlank()
                ? LocalDate.now()
                : parseDate(paidAt, "paid_at");
        if (paymentDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("paid_at cannot be in the future");
        }

        String paymentProvider = normalizedCode(provider, "provider");
        if (!paymentProvider.matches("[a-z0-9][a-z0-9_-]{0,59}")) {
            throw new IllegalArgumentException("provider must be a lowercase slug up to 60 characters");
        }
        String externalId = requireText(transactionId, "transaction_id");
        if (externalId.length() > 255) {
            throw new IllegalArgumentException("transaction_id must be 255 characters or fewer");
        }

        return McpViews.payment(paymentService.recordExternal(invoiceId, paymentDate, amount,
                emptyToNull(method), reference == null || reference.isBlank() ? externalId : reference.trim(),
                emptyToNull(notes), paymentProvider, externalId));
    }

    @McpTool(name = "set_invoice_ach_link", title = "Set an invoice ACH link",
            description = "Add or replace the validated Mercury payment link rendered as the Pay with ACH action on an outstanding invoice. Pass a blank URL to remove it.",
            annotations = @McpTool.McpAnnotations(title = "Set an invoice ACH link", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-invoices')")
    public InvoicePaymentLink setInvoiceAchLink(
            @McpToolParam(description = "Talli invoice ID", required = true) Long invoiceId,
            @McpToolParam(description = "HTTPS Mercury payment URL; blank removes the link", required = true) String paymentUrl) {
        if (invoiceId == null) throw new IllegalArgumentException("invoice_id is required");
        Invoice invoice = invoiceService.updateMercuryPaymentUrl(invoiceId, paymentUrl);
        return new InvoicePaymentLink(invoice.getId(), invoice.getReference(),
                invoice.getMercuryPaymentUrl(), invoice.getMercuryPaymentUrl() == null ? null : "Pay with ACH");
    }

    @McpTool(name = "preview_client_email", title = "Preview a client email",
            description = "Render a no-send preview addressed to an existing Talli client's saved email. Returns the fixed CC, plain and HTML bodies, and a token binding the exact recipient, content, template, and signature choice for send_client_email.",
            annotations = @McpTool.McpAnnotations(title = "Preview a client email", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('send-emails')")
    public EmailPreview previewClientEmail(
            @McpToolParam(description = "Existing client ID; preview uses that client's saved email address", required = true) Long clientId,
            @McpToolParam(description = "Email subject", required = true) String subject,
            @McpToolParam(description = "Plain-text email body", required = true) String body,
            @McpToolParam(description = "Optional template: branded, branded-notice, formal, or minimal", required = false) String templateId,
            @McpToolParam(description = "Include the authenticated agent user's configured signature; defaults to true", required = false) Boolean includeSignature) {
        if (clientId == null) throw new IllegalArgumentException("client_id is required");
        AgentEmailService.Preview preview = agentEmailService.preview(
                authenticatedEmail(), clientId, subject, body, templateId,
                includeSignature == null || includeSignature);
        return new EmailPreview(preview.clientId(), preview.toAddress(), preview.ccAddress(),
                preview.subject(), preview.body(), preview.bodyHtml(), preview.templateId(),
                preview.signatureIncluded(), preview.previewToken());
    }

    @McpTool(name = "send_client_email", title = "Send a client email",
            description = "Send an explicitly approved client email exactly as returned by preview_client_email and audit it in Talli. Requires the matching preview_token and confirm_send=true. Every agent email visibly CCs the configured MCP_EMAIL_CC address.",
            annotations = @McpTool.McpAnnotations(title = "Send a client email", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = false, openWorldHint = true))
    @PreAuthorize("hasAuthority('send-emails')")
    public SentEmail sendClientEmail(
            @McpToolParam(description = "Existing client ID; email is sent only to that client's saved email address", required = true) Long clientId,
            @McpToolParam(description = "Approved email subject", required = true) String subject,
            @McpToolParam(description = "Approved plain-text email body", required = true) String body,
            @McpToolParam(description = "Optional template: branded, branded-notice, formal, or minimal", required = false) String templateId,
            @McpToolParam(description = "Include the authenticated agent user's configured signature; defaults to true", required = false) Boolean includeSignature,
            @McpToolParam(description = "Token returned by preview_client_email for these exact inputs", required = true) String previewToken,
            @McpToolParam(description = "Must be true only after a human approves this exact recipient, subject, and body", required = true) Boolean confirmSend) {
        if (clientId == null) throw new IllegalArgumentException("client_id is required");

        AgentEmailService.SendResult result = agentEmailService.send(
                authenticatedEmail(), clientId, subject, body, templateId,
                includeSignature == null || includeSignature, previewToken,
                Boolean.TRUE.equals(confirmSend));
        var email = result.email();
        return new SentEmail(email.getId(), clientId, email.getToAddress(), email.getCc(),
                email.getSubject(), email.getStatus(), email.getSentAt(), result.templateId(),
                result.signatureIncluded(), email.getErrorMessage());
    }

    private static String authenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("Authenticated MCP user is required");
        }
        return authentication.getName();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String normalizedCode(String value, String name) {
        return requireText(value, name).toLowerCase(Locale.ROOT);
    }

    private static String currency(String value) {
        String code = value == null || value.isBlank() ? "USD" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }
        return code;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate parseDate(String value, String name) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(name + " must be an ISO date (YYYY-MM-DD)");
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("started_at must be an ISO local date-time");
        }
    }

    public record InvoicePaymentLink(Long invoiceId, String invoiceReference,
                                     String paymentUrl, String buttonLabel) {}

    public record EmailPreview(Long clientId, String toAddress, String ccAddress,
                               String subject, String body, String bodyHtml,
                               String templateId, boolean signatureIncluded,
                               String previewToken) {}

    public record SentEmail(Long emailId, Long clientId, String toAddress, String ccAddress,
                            String subject, String status, LocalDateTime sentAt, String templateId,
                            boolean signatureIncluded, String errorMessage) {}
}
