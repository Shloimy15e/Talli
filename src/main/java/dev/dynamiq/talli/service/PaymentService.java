package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Payment;
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

    public PaymentService(PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          ExchangeRateService exchangeRateService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.exchangeRateService = exchangeRateService;
    }

    public List<Payment> listForInvoice(Long invoiceId) {
        return paymentRepository.findByInvoiceIdOrderByPaidAtDescIdDesc(invoiceId);
    }

    /**
     * Record a payment against an invoice. Recomputes amount_paid from the
     * authoritative sum (don't increment the cache — drift is a real bug class)
     * and transitions status to "paid" when the balance hits zero or below.
     */
    @Transactional
    public Payment record(Long invoiceId, LocalDate paidAt, BigDecimal amount,
                          String method, String reference, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive.");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setPaidAt(paidAt != null ? paidAt : LocalDate.now());
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setReference(reference);
        payment.setNotes(notes);
        payment.setExchangeRate(exchangeRateService.getRate(invoice.getCurrency()));
        payment = paymentRepository.save(payment);

        // Force flush so the new payment is visible to the sum query below.
        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoiceId);
        invoice.setAmountPaid(totalPaid);

        if (invoice.balance().signum() <= 0
                && !"paid".equals(invoice.getStatus())
                && !"void".equals(invoice.getStatus())) {
            invoice.setStatus("paid");
            invoice.setPaidInFullBy(LocalDateTime.now());
        }

        return payment;
    }

    /**
     * Delete a payment and recompute the invoice's amount_paid + status.
     * If removing this payment drops the balance back above zero, status
     * reverts to "unpaid" (or "overdue" if past due).
     */
    @Transactional
    public void delete(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        Invoice invoice = payment.getInvoice();

        paymentRepository.delete(payment);
        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
        invoice.setAmountPaid(totalPaid);

        if (invoice.balance().signum() > 0 && "paid".equals(invoice.getStatus())) {
            invoice.setPaidInFullBy(null);
            boolean overdue = invoice.getDueAt() != null && invoice.getDueAt().isBefore(LocalDate.now());
            invoice.setStatus(overdue ? "overdue" : "unpaid");
        }
    }
}
