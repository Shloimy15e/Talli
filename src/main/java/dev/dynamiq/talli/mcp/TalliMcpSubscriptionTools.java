package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import dev.dynamiq.talli.service.SubscriptionService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Component
public class TalliMcpSubscriptionTools {

    private static final Set<String> CYCLES = Set.of("monthly", "yearly");

    private final SubscriptionRepository subscriptions;
    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final ExpenseRepository expenses;
    private final SubscriptionService subscriptionService;

    public TalliMcpSubscriptionTools(SubscriptionRepository subscriptions,
                                     ClientRepository clients,
                                     ProjectRepository projects,
                                     ExpenseRepository expenses,
                                     SubscriptionService subscriptionService) {
        this.subscriptions = subscriptions;
        this.clients = clients;
        this.projects = projects;
        this.expenses = expenses;
        this.subscriptionService = subscriptionService;
    }

    @McpTool(name = "create_subscription", title = "Create a subscription",
            description = "Create an active recurring-expense template. If a project is supplied, its client is used and any supplied client_id must match.",
            annotations = @McpTool.McpAnnotations(title = "Create a subscription", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-expenses')")
    @Transactional
    public McpViews.SubscriptionView createSubscription(
            @McpToolParam(description = "Vendor name", required = true) String vendor,
            @McpToolParam(description = "Category: software, hardware, travel, meals, contractors, office, marketing, taxes, or other", required = true) String category,
            @McpToolParam(description = "Positive recurring amount", required = true) BigDecimal amount,
            @McpToolParam(description = "Billing cycle: monthly or yearly", required = true) String cycle,
            @McpToolParam(description = "Subscription start date, YYYY-MM-DD", required = true) String startedOn,
            @McpToolParam(description = "Three-letter currency code; defaults to USD", required = false) String currency,
            @McpToolParam(description = "Optional client ID", required = false) Long clientId,
            @McpToolParam(description = "Optional project ID", required = false) Long projectId,
            @McpToolParam(description = "Optional description", required = false) String description,
            @McpToolParam(description = "Optional next due date, YYYY-MM-DD; defaults to started_on", required = false) String nextDueOn,
            @McpToolParam(description = "Optional subscription management URL", required = false) String manageUrl,
            @McpToolParam(description = "Optional cancellation URL", required = false) String cancelUrl,
            @McpToolParam(description = "Optional payment method", required = false) String paymentMethod) {
        Subscription subscription = new Subscription();
        subscription.setVendor(requireText(vendor, "vendor"));
        subscription.setCategory(category(category));
        subscription.setAmount(amount(amount));
        subscription.setCycle(cycle(cycle));
        subscription.setCurrency(currency(currency));
        subscription.setStartedOn(parseDate(startedOn, "started_on"));
        subscription.setNextDueOn(nextDueOn == null || nextDueOn.isBlank()
                ? subscription.getStartedOn() : parseDate(nextDueOn, "next_due_on"));
        subscription.setDescription(emptyToNull(description));
        subscription.setManageUrl(emptyToNull(manageUrl));
        subscription.setCancelUrl(emptyToNull(cancelUrl));
        subscription.setPaymentMethod(emptyToNull(paymentMethod));

        Associations associations = associations(null, null, clientId, projectId);
        subscription.setClient(associations.client());
        subscription.setProject(associations.project());
        validateSchedule(subscription);
        return McpViews.subscription(subscriptions.save(subscription));
    }

    @McpTool(name = "update_subscription", title = "Update a subscription",
            description = "Update future subscription settings. Existing expenses are unchanged. Blank optional text clears it; client_id or project_id 0 clears that association. Use cancel_subscription or reactivate_subscription for lifecycle changes.",
            annotations = @McpTool.McpAnnotations(title = "Update a subscription", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-expenses')")
    @Transactional
    public McpViews.SubscriptionView updateSubscription(
            @McpToolParam(description = "Existing subscription ID", required = true) Long subscriptionId,
            @McpToolParam(description = "Optional vendor name", required = false) String vendor,
            @McpToolParam(description = "Optional expense category", required = false) String category,
            @McpToolParam(description = "Optional positive recurring amount", required = false) BigDecimal amount,
            @McpToolParam(description = "Optional billing cycle: monthly or yearly", required = false) String cycle,
            @McpToolParam(description = "Optional start date, YYYY-MM-DD", required = false) String startedOn,
            @McpToolParam(description = "Optional three-letter currency code", required = false) String currency,
            @McpToolParam(description = "Optional client ID; 0 clears it unless a project remains assigned", required = false) Long clientId,
            @McpToolParam(description = "Optional project ID; 0 clears it and keeps the current client", required = false) Long projectId,
            @McpToolParam(description = "Optional description; blank clears it", required = false) String description,
            @McpToolParam(description = "Optional next due date, YYYY-MM-DD; blank clears it", required = false) String nextDueOn,
            @McpToolParam(description = "Optional management URL; blank clears it", required = false) String manageUrl,
            @McpToolParam(description = "Optional cancellation URL; blank clears it", required = false) String cancelUrl,
            @McpToolParam(description = "Optional payment method; blank clears it", required = false) String paymentMethod) {
        if (subscriptionId == null) throw new IllegalArgumentException("subscription_id is required");
        if (vendor == null && category == null && amount == null && cycle == null && startedOn == null
                && currency == null && clientId == null && projectId == null && description == null
                && nextDueOn == null && manageUrl == null && cancelUrl == null && paymentMethod == null) {
            throw new IllegalArgumentException("At least one subscription field is required");
        }

        Subscription subscription = findSubscription(subscriptionId);
        if (vendor != null) subscription.setVendor(requireText(vendor, "vendor"));
        if (category != null) subscription.setCategory(category(category));
        if (amount != null) subscription.setAmount(amount(amount));
        if (cycle != null) subscription.setCycle(cycle(cycle));
        if (startedOn != null) subscription.setStartedOn(parseDate(startedOn, "started_on"));
        if (currency != null) subscription.setCurrency(currency(currency));
        if (description != null) subscription.setDescription(emptyToNull(description));
        if (nextDueOn != null) subscription.setNextDueOn(nextDueOn.isBlank()
                ? null : parseDate(nextDueOn, "next_due_on"));
        if (manageUrl != null) subscription.setManageUrl(emptyToNull(manageUrl));
        if (cancelUrl != null) subscription.setCancelUrl(emptyToNull(cancelUrl));
        if (paymentMethod != null) subscription.setPaymentMethod(emptyToNull(paymentMethod));

        Associations associations = associations(subscription.getClient(), subscription.getProject(),
                clientId, projectId);
        subscription.setClient(associations.client());
        subscription.setProject(associations.project());
        validateSchedule(subscription);
        return McpViews.subscription(subscriptions.save(subscription));
    }

    @McpTool(name = "delete_subscription", title = "Delete a subscription",
            description = "Permanently delete a recurring template while preserving and unlinking its historical expenses. Set confirmed=true only after explicit user confirmation.",
            annotations = @McpTool.McpAnnotations(title = "Delete a subscription", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-expenses')")
    @Transactional
    public SubscriptionDeleteResult deleteSubscription(
            @McpToolParam(description = "Existing subscription ID", required = true) Long subscriptionId,
            @McpToolParam(description = "Must be true after explicit user confirmation", required = true) Boolean confirmed) {
        if (subscriptionId == null) throw new IllegalArgumentException("subscription_id is required");
        if (!Boolean.TRUE.equals(confirmed)) {
            throw new IllegalArgumentException("confirmed must be true to permanently delete a subscription");
        }
        Subscription subscription = findSubscription(subscriptionId);
        int unlinkedExpenses = subscriptionService.delete(subscription);
        return new SubscriptionDeleteResult(subscriptionId, unlinkedExpenses, true);
    }

    @McpTool(name = "cancel_subscription", title = "Cancel a subscription",
            description = "Mark a subscription cancelled and clear its next due date without deleting its history.",
            annotations = @McpTool.McpAnnotations(title = "Cancel a subscription", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-expenses')")
    @Transactional
    public McpViews.SubscriptionView cancelSubscription(
            @McpToolParam(description = "Existing subscription ID", required = true) Long subscriptionId,
            @McpToolParam(description = "Optional cancellation date, YYYY-MM-DD; defaults to today", required = false) String cancelledOn) {
        if (subscriptionId == null) throw new IllegalArgumentException("subscription_id is required");
        LocalDate date = cancelledOn == null || cancelledOn.isBlank()
                ? LocalDate.now() : parseDate(cancelledOn, "cancelled_on");
        return McpViews.subscription(subscriptionService.cancel(findSubscription(subscriptionId), date));
    }

    @McpTool(name = "reactivate_subscription", title = "Reactivate a subscription",
            description = "Reactivate a cancelled subscription and set its next due date.",
            annotations = @McpTool.McpAnnotations(title = "Reactivate a subscription", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-expenses')")
    @Transactional
    public McpViews.SubscriptionView reactivateSubscription(
            @McpToolParam(description = "Existing subscription ID", required = true) Long subscriptionId,
            @McpToolParam(description = "Optional next due date, YYYY-MM-DD; defaults to today or started_on if later", required = false) String nextDueOn) {
        if (subscriptionId == null) throw new IllegalArgumentException("subscription_id is required");
        Subscription subscription = findSubscription(subscriptionId);
        LocalDate defaultDueDate = LocalDate.now().isAfter(subscription.getStartedOn())
                ? LocalDate.now() : subscription.getStartedOn();
        LocalDate date = nextDueOn == null || nextDueOn.isBlank()
                ? defaultDueDate : parseDate(nextDueOn, "next_due_on");
        if (date.isBefore(subscription.getStartedOn())) {
            throw new IllegalArgumentException("next_due_on cannot be before started_on");
        }
        return McpViews.subscription(subscriptionService.reactivate(subscription, date));
    }

    @McpTool(name = "record_subscription_charge", title = "Record a subscription charge",
            description = "Create an expense from an active subscription and advance its next due date. If a bank expense already exists, use link_expense_to_subscription instead to avoid duplication.",
            annotations = @McpTool.McpAnnotations(title = "Record a subscription charge", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-expenses')")
    @Transactional
    public McpViews.ExpenseView recordSubscriptionCharge(
            @McpToolParam(description = "Existing active subscription ID", required = true) Long subscriptionId,
            @McpToolParam(description = "Optional paid date, YYYY-MM-DD; defaults to today", required = false) String paidOn) {
        if (subscriptionId == null) throw new IllegalArgumentException("subscription_id is required");
        Subscription subscription = findSubscription(subscriptionId);
        if (!subscription.isActive()) {
            throw new IllegalStateException("Cancelled subscriptions cannot record charges");
        }
        LocalDate date = paidOn == null || paidOn.isBlank()
                ? LocalDate.now() : parseDate(paidOn, "paid_on");
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("paid_on cannot be in the future");
        }
        return McpViews.expense(subscriptionService.recordCharge(subscription, date));
    }

    @McpTool(name = "link_expense_to_subscription", title = "Link an expense to a subscription",
            description = "Link an existing expense to a recurring template. Compatible missing client/project tags are inherited; cross-client or cross-project links are rejected.",
            annotations = @McpTool.McpAnnotations(title = "Link an expense to a subscription", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-expenses')")
    @Transactional
    public McpViews.ExpenseView linkExpenseToSubscription(
            @McpToolParam(description = "Existing expense ID", required = true) Long expenseId,
            @McpToolParam(description = "Existing subscription ID", required = true) Long subscriptionId) {
        if (expenseId == null) throw new IllegalArgumentException("expense_id is required");
        if (subscriptionId == null) throw new IllegalArgumentException("subscription_id is required");
        Expense expense = findExpense(expenseId);
        return McpViews.expense(subscriptionService.linkExpense(expense, findSubscription(subscriptionId)));
    }

    @McpTool(name = "unlink_expense_from_subscription", title = "Unlink an expense from a subscription",
            description = "Remove only the recurring-template link from an expense. Client, project, amount, and all other expense data remain unchanged.",
            annotations = @McpTool.McpAnnotations(title = "Unlink an expense from a subscription", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAuthority('manage-expenses')")
    @Transactional
    public McpViews.ExpenseView unlinkExpenseFromSubscription(
            @McpToolParam(description = "Existing expense ID", required = true) Long expenseId) {
        if (expenseId == null) throw new IllegalArgumentException("expense_id is required");
        return McpViews.expense(subscriptionService.unlinkExpense(findExpense(expenseId)));
    }

    private Subscription findSubscription(Long id) {
        return subscriptions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + id));
    }

    private Expense findExpense(Long id) {
        return expenses.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: " + id));
    }

    private Associations associations(Client currentClient, Project currentProject,
                                      Long clientId, Long projectId) {
        Project project = currentProject;
        Client client = currentClient;
        if (projectId != null) {
            project = projectId == 0 ? null : projects.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        }
        if (clientId != null) {
            client = clientId == 0 ? null : clients.findById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        }
        if (project != null) {
            if (client != null && !project.getClient().getId().equals(client.getId())) {
                throw new IllegalArgumentException("project_id does not belong to client_id");
            }
            client = project.getClient();
        }
        return new Associations(client, project);
    }

    private static void validateSchedule(Subscription subscription) {
        if (subscription.getNextDueOn() != null
                && subscription.getNextDueOn().isBefore(subscription.getStartedOn())) {
            throw new IllegalArgumentException("next_due_on cannot be before started_on");
        }
    }

    private static String category(String value) {
        String category = normalizedCode(value, "category");
        if (!Expense.CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Unknown category: " + value);
        }
        return category;
    }

    private static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        return value;
    }

    private static String cycle(String value) {
        String cycle = normalizedCode(value, "cycle");
        if (!CYCLES.contains(cycle)) {
            throw new IllegalArgumentException("cycle must be monthly or yearly");
        }
        return cycle;
    }

    private static String currency(String value) {
        String code = value == null || value.isBlank() ? "USD" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }
        return code;
    }

    private static LocalDate parseDate(String value, String name) {
        try {
            return LocalDate.parse(requireText(value, name));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(name + " must be an ISO date (YYYY-MM-DD)");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String normalizedCode(String value, String name) {
        return requireText(value, name).toLowerCase(Locale.ROOT);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Associations(Client client, Project project) {}

    public record SubscriptionDeleteResult(Long subscriptionId, int unlinkedExpenseCount,
                                           boolean deleted) {}
}
