package dev.dynamiq.talli.integration.mercury;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.InvoiceItem;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.InvoiceItemRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
public class MercuryInvoiceSyncService {

    private static final Logger log = LoggerFactory.getLogger(MercuryInvoiceSyncService.class);

    private final MercuryProperties properties;
    private final MercuryClient mercuryClient;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ClientRepository clientRepository;
    private final PaymentService paymentService;
    private final TransactionTemplate transactionTemplate;

    public MercuryInvoiceSyncService(
            MercuryProperties properties,
            MercuryClient mercuryClient,
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            ClientRepository clientRepository,
            PaymentService paymentService,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.mercuryClient = mercuryClient;
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.clientRepository = clientRepository;
        this.paymentService = paymentService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean isEnabled() {
        return properties.isConfigured();
    }

    /** Retry unsent invoices and refresh open Mercury invoices independently. */
    public void syncOutstandingInvoices() {
        if (!isEnabled()) return;

        for (Long invoiceId : invoiceRepository.findMercurySyncCandidateIds()) {
            try {
                transactionTemplate.executeWithoutResult(ignored -> syncInvoiceInternal(invoiceId));
            } catch (RuntimeException e) {
                log.error("Mercury sync transaction failed for invoice {}: {}", invoiceId, e.getMessage());
            }
        }
    }

    @Transactional
    public SyncResult syncInvoice(Long invoiceId) {
        if (!isEnabled()) return SyncResult.failed(properties.configurationError());
        return syncInvoiceInternal(invoiceId);
    }

    private SyncResult syncInvoiceInternal(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForMercurySync(invoiceId).orElseThrow();

        if (invoice.getMercuryInvoiceId() == null
                && ("paid".equals(invoice.getStatus()) || "void".equals(invoice.getStatus()))) {
            return fail(invoice, "Paid or void invoices are not newly sent to Mercury.");
        }

        Client client = clientRepository.findByIdForMercurySync(invoice.getClient().getId()).orElseThrow();
        if (client.getEmail() == null || client.getEmail().isBlank()) {
            return fail(invoice, "Client must have an email address before syncing to Mercury.");
        }

        try {
            String customerId = ensureCustomer(client);
            MercuryClient.Invoice mercuryInvoice = invoice.getMercuryInvoiceId() == null
                    ? createInvoice(invoice, customerId)
                    : mercuryClient.getInvoice(invoice.getMercuryInvoiceId());

            validateMercuryInvoice(invoice, mercuryInvoice, customerId);
            applyMercuryState(invoice, mercuryInvoice);
            if ("Paid".equals(mercuryInvoice.status()) && !"void".equals(invoice.getStatus())) {
                paymentService.recordExternal(
                        invoice.getId(),
                        paymentDate(mercuryInvoice.updatedAt()),
                        "mercury",
                        mercuryInvoice.id(),
                        "mercury",
                        "Automatically synced from Mercury invoice " + invoice.getReference() + ".");
            }
            return SyncResult.succeeded("Mercury invoice is " + mercuryInvoice.status().toLowerCase() + ".");
        } catch (MercuryApiException | IllegalStateException e) {
            return fail(invoice, "Mercury sync failed: " + cleanMessage(e));
        }
    }

    private String ensureCustomer(Client client) {
        if (client.getMercuryCustomerId() != null) return client.getMercuryCustomerId();

        MercuryClient.Customer customer = mercuryClient.createCustomer(client.getName(), client.getEmail());
        client.setMercuryCustomerId(customer.id());
        clientRepository.flush();
        return customer.id();
    }

    private MercuryClient.Invoice createInvoice(Invoice invoice, String customerId) {
        LocalDate invoiceDate = invoice.getIssuedAt() == null ? LocalDate.now() : invoice.getIssuedAt();
        LocalDate dueDate = invoice.getDueAt() == null
                ? invoiceDate.plusDays(paymentTerms(invoice.getClient()))
                : invoice.getDueAt();

        List<MercuryClient.LineItem> lineItems = invoiceItemRepository
                .findByInvoiceIdOrderByIdAsc(invoice.getId()).stream()
                .map(this::toMercuryLineItem)
                .toList();
        if (lineItems.isEmpty()) {
            throw new IllegalStateException("Invoice has no line items.");
        }

        MercuryClient.CreateInvoiceRequest request = new MercuryClient.CreateInvoiceRequest(
                properties.achDebitEnabled(),
                List.of(),
                properties.creditCardEnabled(),
                invoice.getCurrency(),
                customerId,
                properties.destinationAccountId(),
                dueDate.toString(),
                "Talli invoice ID " + invoice.getId(),
                invoiceDate.toString(),
                invoice.getReference(),
                lineItems,
                invoice.getNotes(),
                properties.sendInvoices() ? "SendNow" : "DontSend",
                dateString(invoice.getPeriodEnd()),
                dateString(invoice.getPeriodStart()),
                properties.useRealAccountNumber());

        MercuryClient.Invoice created = mercuryClient.createInvoice(request);
        if (properties.sendInvoices() && invoice.getSentAt() == null) {
            invoice.setSentAt(LocalDateTime.now());
        }
        return created;
    }

    private MercuryClient.LineItem toMercuryLineItem(InvoiceItem item) {
        String name = item.getDescription() == null || item.getDescription().isBlank()
                ? "Invoice item"
                : item.getDescription();
        return new MercuryClient.LineItem(name, item.getUnitCount(), item.getUnitPrice());
    }

    private void applyMercuryState(Invoice invoice, MercuryClient.Invoice mercuryInvoice) {
        invoice.setMercuryInvoiceId(mercuryInvoice.id());
        invoice.setMercuryInvoiceSlug(mercuryInvoice.slug());
        invoice.setMercuryStatus(mercuryInvoice.status());
        invoice.setMercurySyncedAt(LocalDateTime.now());
        invoice.setMercurySyncError(null);
        invoiceRepository.flush();
    }

    private void validateMercuryInvoice(
            Invoice invoice, MercuryClient.Invoice mercuryInvoice, String customerId) {
        if (mercuryInvoice.id() == null || mercuryInvoice.id().isBlank()) {
            throw new IllegalStateException("Mercury returned an invoice without an ID.");
        }
        if (mercuryInvoice.slug() == null || mercuryInvoice.slug().isBlank()) {
            throw new IllegalStateException("Mercury returned an invoice without a payment link.");
        }
        if (mercuryInvoice.amount() == null
                || invoice.getAmount().compareTo(mercuryInvoice.amount()) != 0) {
            throw new IllegalStateException("Mercury invoice amount does not match the local invoice.");
        }
        if (!invoice.getCurrency().equals(mercuryInvoice.currencyCode())) {
            throw new IllegalStateException("Mercury invoice currency does not match the local invoice.");
        }
        if (!invoice.getReference().equals(mercuryInvoice.invoiceNumber())) {
            throw new IllegalStateException("Mercury invoice number does not match the local invoice.");
        }
        if (!customerId.equals(mercuryInvoice.customerId())) {
            throw new IllegalStateException("Mercury invoice customer does not match the local client.");
        }
        if (!Set.of("Unpaid", "Paid", "Cancelled", "Processing").contains(mercuryInvoice.status())) {
            throw new IllegalStateException("Mercury returned an unknown invoice status.");
        }
    }

    private SyncResult fail(Invoice invoice, String message) {
        invoice.setMercurySyncError(message);
        invoiceRepository.flush();
        log.warn("{} Local invoice: {}", message, invoice.getId());
        return SyncResult.failed(message);
    }

    private static int paymentTerms(Client client) {
        return client.getPaymentTermsDays() == null ? 30 : client.getPaymentTermsDays();
    }

    private static String dateString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static LocalDate paymentDate(String updatedAt) {
        if (updatedAt == null || updatedAt.isBlank()) return LocalDate.now();
        try {
            return OffsetDateTime.parse(updatedAt).toLocalDate();
        } catch (RuntimeException ignored) {
            return LocalDate.now();
        }
    }

    private static String cleanMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record SyncResult(boolean success, String message) {
        static SyncResult succeeded(String message) { return new SyncResult(true, message); }
        static SyncResult failed(String message) { return new SyncResult(false, message); }
    }
}
