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
        BigDecimal balance = invoice.balance();
        if (amount != null && amount.compareTo(balance) > 0) {
            throw new IllegalStateException(
                    "Payment (" + amount + ") exceeds outstanding balance (" + balance + ").");
        }
        Payment payment = buildPayment(invoice, paidAt, amount, method, reference, notes);
        payment.setSource("direct");
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

    /** Recompute amount_paid from SUM and adjust invoice status accordingly. */
    private void syncInvoice(Invoice invoice) {
        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
        invoice.setAmountPaid(totalPaid);

        if (invoice.balance().signum() <= 0
                && !"paid".equals(invoice.getStatus())
                && !"void".equals(invoice.getStatus())) {
            invoice.setStatus("paid");
            invoice.setPaidInFullBy(LocalDateTime.now());
        } else if (invoice.balance().signum() > 0 && "paid".equals(invoice.getStatus())) {
            invoice.setPaidInFullBy(null);
            boolean overdue = invoice.getDueAt() != null && invoice.getDueAt().isBefore(LocalDate.now());
            invoice.setStatus(overdue ? "overdue" : "unpaid");
        }
    }
}
