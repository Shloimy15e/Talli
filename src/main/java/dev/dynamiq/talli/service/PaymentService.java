package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.ClientCredit;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Payment;
import dev.dynamiq.talli.repository.ClientCreditRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ExchangeRateService exchangeRateService;
    private final ClientCreditRepository creditRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          ExchangeRateService exchangeRateService,
                          ClientCreditRepository creditRepository) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.exchangeRateService = exchangeRateService;
        this.creditRepository = creditRepository;
    }

    public List<Payment> listForInvoice(Long invoiceId) {
        return paymentRepository.findByInvoiceIdOrderByPaidAtDescIdDesc(invoiceId);
    }

    /**
     * Record a direct payment (fresh cash) against an invoice.
     * Caps at the outstanding balance — can't overpay.
     */
    @Transactional
    public Payment record(Long invoiceId, LocalDate paidAt, BigDecimal amount,
                          String method, String reference, String notes) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        Payment payment = buildPayment(invoice, paidAt, amount, method, reference, notes);
        payment.setSource("direct");
        payment = paymentRepository.save(payment);
        syncInvoice(invoice);
        return payment;
    }

    /**
     * Record a bank-sourced payment exactly once. Provider plus external ID is
     * the stable identity, so identifiers from different banks cannot collide.
     */
    @Transactional
    public Payment recordExternal(Long invoiceId, LocalDate paidAt, BigDecimal amount,
                                  String method, String reference, String notes,
                                  String externalProvider, String externalId) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive.");
        }
        String provider = requireText(externalProvider, "External provider").toLowerCase(Locale.ROOT);
        String transactionId = requireText(externalId, "External transaction ID");
        LocalDate effectivePaidAt = paidAt != null ? paidAt : LocalDate.now();

        var existing = paymentRepository.findByExternalProviderAndExternalId(provider, transactionId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            boolean samePayment = payment.getInvoice().getId().equals(invoiceId)
                    && payment.getAmount().compareTo(amount) == 0
                    && payment.getPaidAt().equals(effectivePaidAt);
            if (!samePayment) {
                throw new IllegalStateException(
                        "External transaction is already linked to a different payment.");
            }
            return payment;
        }

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        Payment payment = buildPayment(invoice, effectivePaidAt, amount, method, reference, notes);
        payment.setSource("direct");
        payment.setExternalProvider(provider);
        payment.setExternalId(transactionId);
        payment = paymentRepository.save(payment);
        syncInvoice(invoice);
        return payment;
    }

    /**
     * Apply a client credit to an invoice. Validates credit belongs to the
     * invoice's client, currency matches, and enough balance remains.
     */
    @Transactional
    public Payment applyCredit(Long invoiceId, Long creditId, LocalDate paidAt,
                               BigDecimal amount, String notes) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        ClientCredit credit = creditRepository.findById(creditId).orElseThrow();

        if (!credit.getClient().getId().equals(invoice.getClient().getId())) {
            throw new IllegalStateException("Credit belongs to a different client.");
        }
        if (!credit.getCurrency().equals(invoice.getCurrency())) {
            throw new IllegalStateException(
                    "Credit currency (" + credit.getCurrency() + ") does not match invoice currency ("
                    + invoice.getCurrency() + ").");
        }
        BigDecimal remaining = creditRepository.remainingBalance(credit.getId());
        if (remaining == null) remaining = BigDecimal.ZERO;
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalStateException(
                    "Credit has only " + remaining + " " + credit.getCurrency() + " available.");
        }

        Payment payment = buildPayment(invoice, paidAt, amount, "credit",
                "Credit #" + credit.getId(), notes);
        payment.setSource("credit");
        payment.setCredit(credit);
        payment = paymentRepository.save(payment);
        syncInvoice(invoice);
        return payment;
    }

    /**
     * Delete a payment and recompute the invoice's amount_paid + status.
     * If this was credit-sourced, the credit balance auto-restores since
     * remainingBalance() is derived from the current set of payments.
     */
    @Transactional
    public void delete(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        Invoice invoice = payment.getInvoice();

        paymentRepository.delete(payment);
        syncInvoice(invoice);
    }

    // --- helpers ---

    private Payment buildPayment(Invoice invoice, LocalDate paidAt, BigDecimal amount,
                                 String method, String reference, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive.");
        }
        if (amount.compareTo(invoice.balance()) > 0) {
            throw new IllegalStateException(
                    "Payment (" + amount + ") exceeds outstanding balance (" + invoice.balance() + ").");
        }
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setPaidAt(paidAt != null ? paidAt : LocalDate.now());
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setReference(reference);
        payment.setNotes(notes);
        payment.setExchangeRate(exchangeRateService.getRate(invoice.getCurrency()));
        return payment;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    /** Recompute amount_paid from SUM and adjust invoice status accordingly. */
    private void syncInvoice(Invoice invoice) {
        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
        invoice.setAmountPaid(totalPaid);

        if ("void".equals(invoice.getStatus())) return;

        if (invoice.balance().signum() <= 0) {
            if (invoice.hasWriteOff()) {
                invoice.setStatus("written_off");
                invoice.setPaidInFullBy(null);
            } else if (!"paid".equals(invoice.getStatus())) {
                invoice.setStatus("paid");
                invoice.setPaidInFullBy(LocalDateTime.now());
            }
            return;
        }

        invoice.setPaidInFullBy(null);
        boolean overdue = invoice.getDueAt() != null && invoice.getDueAt().isBefore(LocalDate.now());
        invoice.setStatus(overdue ? "overdue" : "unpaid");
    }
}
