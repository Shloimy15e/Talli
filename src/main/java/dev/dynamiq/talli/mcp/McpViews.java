package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.InvoiceItem;
import dev.dynamiq.talli.model.Payment;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.service.TimeEntryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Scalar MCP responses that never expose lazy JPA entities or sensitive internals. */
public final class McpViews {

    private McpViews() {}

    public static ClientView client(Client client) {
        return new ClientView(client.getId(), client.getName(), client.getEmail(), client.getPhone(),
                client.getBillingAddress(), client.getPaymentTermsDays(), client.getNotes(),
                client.getCreatedAt(), client.getUpdatedAt());
    }

    public static ProjectView project(Project project) {
        Client client = project.getClient();
        return new ProjectView(project.getId(), project.getName(),
                client != null ? client.getId() : null,
                client != null ? client.getName() : null,
                project.getRateType(), project.getCurrentRate(), project.getCurrency(),
                project.getBillingFrequency(), project.getStatus(), project.getBillable(),
                project.getNotes(), project.getCreatedAt(), project.getUpdatedAt());
    }

    public static TimeEntryView timeEntry(TimeEntry entry) {
        Project project = entry.getProject();
        Client client = project != null ? project.getClient() : null;
        return new TimeEntryView(entry.getId(),
                project != null ? project.getId() : null,
                project != null ? project.getName() : null,
                client != null ? client.getId() : null,
                client != null ? client.getName() : null,
                entry.getStartedAt(), entry.getEndedAt(),
                TimeEntryService.minutesFor(entry, LocalDateTime.now()), entry.isRunning(),
                entry.getDescription(), entry.getBillable(), entry.getBilled(),
                entry.getInvoice() != null ? entry.getInvoice().getId() : null);
    }

    public static ExpenseView expense(Expense expense) {
        return new ExpenseView(expense.getId(),
                expense.getClient() != null ? expense.getClient().getId() : null,
                expense.getClient() != null ? expense.getClient().getName() : null,
                expense.getProject() != null ? expense.getProject().getId() : null,
                expense.getProject() != null ? expense.getProject().getName() : null,
                expense.getIncurredOn(), expense.getAmount(), expense.getCurrency(),
                expense.getExchangeRate(), expense.getCategory(), expense.getVendor(),
                expense.getDescription(), expense.getPaymentMethod(), expense.getReceiptUrl(),
                expense.getBillable(), expense.getBilled(),
                expense.getInvoice() != null ? expense.getInvoice().getId() : null);
    }

    public static InvoiceView invoice(Invoice invoice) {
        return new InvoiceView(invoice.getId(), invoice.getReference(),
                invoice.getClient().getId(), invoice.getClient().getName(),
                invoice.getAmount(), invoice.getAmountPaid(), invoice.balance(),
                invoice.getCurrency(), invoice.getExchangeRate(), invoice.getStatus(),
                invoice.getNotes(), invoice.getPeriodStart(), invoice.getPeriodEnd(),
                invoice.getIssuedAt(), invoice.getSentAt(), invoice.getDueAt(),
                invoice.getPaidInFullBy());
    }

    public static InvoiceItemView invoiceItem(InvoiceItem item) {
        return new InvoiceItemView(item.getId(),
                item.getProject() != null ? item.getProject().getId() : null,
                item.getProject() != null ? item.getProject().getName() : null,
                item.getDescription(), item.getUnitPrice(), item.getUnitCount(),
                item.getUnit(), item.getTotal());
    }

    public static PaymentView payment(Payment payment) {
        Invoice invoice = payment.getInvoice();
        return new PaymentView(payment.getId(), invoice.getId(), invoice.getReference(),
                payment.getPaidAt(), payment.getAmount(), invoice.getCurrency(),
                payment.getExchangeRate(), payment.getMethod(), payment.getReference(),
                payment.getNotes(), payment.getSource(), payment.getExternalProvider(),
                payment.getExternalId());
    }

    public static SubscriptionView subscription(Subscription subscription) {
        return new SubscriptionView(subscription.getId(),
                subscription.getClient() != null ? subscription.getClient().getId() : null,
                subscription.getClient() != null ? subscription.getClient().getName() : null,
                subscription.getProject() != null ? subscription.getProject().getId() : null,
                subscription.getProject() != null ? subscription.getProject().getName() : null,
                subscription.getVendor(), subscription.getDescription(), subscription.getCategory(),
                subscription.getAmount(), subscription.getCurrency(), subscription.getCycle(),
                subscription.getStartedOn(), subscription.getCancelledOn(), subscription.getNextDueOn(),
                subscription.getPaymentMethod(), subscription.isActive());
    }

    public record ClientView(Long id, String name, String email, String phone,
                             String billingAddress, Integer paymentTermsDays, String notes,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record ProjectView(Long id, String name, Long clientId, String clientName,
                              String rateType, BigDecimal currentRate, String currency,
                              String billingFrequency, String status, Boolean billable, String notes,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record TimeEntryView(Long id, Long projectId, String projectName,
                                Long clientId, String clientName,
                                LocalDateTime startedAt, LocalDateTime endedAt,
                                int durationMinutes, boolean running, String description,
                                Boolean billable, Boolean billed, Long invoiceId) {}

    public record ExpenseView(Long id, Long clientId, String clientName,
                              Long projectId, String projectName, LocalDate incurredOn,
                              BigDecimal amount, String currency, BigDecimal exchangeRate,
                              String category, String vendor, String description,
                              String paymentMethod, String receiptUrl, Boolean billable,
                              Boolean billed, Long invoiceId) {}

    public record DeleteResult(String entity, Long id, boolean deleted) {}

    public record InvoiceView(Long id, String reference, Long clientId, String clientName,
                              BigDecimal amount, BigDecimal amountPaid, BigDecimal balance,
                              String currency, BigDecimal exchangeRate, String status, String notes,
                              LocalDate periodStart, LocalDate periodEnd, LocalDate issuedAt,
                              LocalDateTime sentAt, LocalDate dueAt, LocalDateTime paidInFullBy) {}

    public record InvoiceItemView(Long id, Long projectId, String projectName,
                                  String description, BigDecimal unitPrice,
                                  BigDecimal unitCount, String unit, BigDecimal total) {}

    public record PaymentView(Long id, Long invoiceId, String invoiceReference,
                              LocalDate paidAt, BigDecimal amount, String currency,
                              BigDecimal exchangeRate, String method, String reference,
                              String notes, String source, String externalProvider,
                              String externalId) {}

    public record SubscriptionView(Long id, Long clientId, String clientName,
                                   Long projectId, String projectName, String vendor,
                                   String description, String category, BigDecimal amount,
                                   String currency, String cycle, LocalDate startedOn,
                                   LocalDate cancelledOn, LocalDate nextDueOn,
                                   String paymentMethod, boolean active) {}
}
