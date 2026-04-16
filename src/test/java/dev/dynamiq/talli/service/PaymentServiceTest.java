package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Payment;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private InvoiceRepository invoiceRepository;
    private PaymentService service;

    private Invoice invoice;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
        when(exchangeRateService.getRate(any())).thenReturn(java.math.BigDecimal.ONE);
        service = new PaymentService(paymentRepository, invoiceRepository, exchangeRateService);

        invoice = new Invoice();
        invoice.setId(1L);
        invoice.setAmount(new BigDecimal("1000.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus("unpaid");

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(10L);
            return p;
        });
    }

    @Test
    void record_savesPaymentAndUpdatesInvoice() {
        when(paymentRepository.sumAmountByInvoiceId(1L)).thenReturn(new BigDecimal("400.00"));

        Payment payment = service.record(1L, LocalDate.now(), new BigDecimal("400.00"),
                "zelle", "ref123", null);

        assertThat(payment.getAmount()).isEqualByComparingTo("400.00");
        assertThat(payment.getMethod()).isEqualTo("zelle");
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("400.00");
        assertThat(invoice.getStatus()).isEqualTo("unpaid"); // balance still > 0
    }

    @Test
    void record_transitionsToPaidWhenBalanceZero() {
        when(paymentRepository.sumAmountByInvoiceId(1L)).thenReturn(new BigDecimal("1000.00"));

        service.record(1L, LocalDate.now(), new BigDecimal("1000.00"), "wire", null, null);

        assertThat(invoice.getStatus()).isEqualTo("paid");
        assertThat(invoice.getPaidInFullBy()).isNotNull();
    }

    @Test
    void record_transitionsToPaidWhenOverpaid() {
        when(paymentRepository.sumAmountByInvoiceId(1L)).thenReturn(new BigDecimal("1200.00"));

        service.record(1L, LocalDate.now(), new BigDecimal("1200.00"), "check", null, null);

        assertThat(invoice.getStatus()).isEqualTo("paid");
    }

    @Test
    void record_doesNotChangePaidToOverdue() {
        invoice.setStatus("paid");
        when(paymentRepository.sumAmountByInvoiceId(1L)).thenReturn(new BigDecimal("1000.00"));

        service.record(1L, LocalDate.now(), new BigDecimal("100.00"), null, null, null);

        assertThat(invoice.getStatus()).isEqualTo("paid"); // stays paid
    }

    @Test
    void record_rejectsZeroAmount() {
        assertThatThrownBy(() -> service.record(1L, LocalDate.now(), BigDecimal.ZERO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void record_rejectsNegativeAmount() {
        assertThatThrownBy(() -> service.record(1L, LocalDate.now(), new BigDecimal("-50"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_recomputesAndRevertsStatus() {
        invoice.setStatus("paid");
        invoice.setAmountPaid(new BigDecimal("1000.00"));
        invoice.setDueAt(LocalDate.now().plusDays(10)); // not overdue

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setInvoice(invoice);
        payment.setAmount(new BigDecimal("1000.00"));

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.sumAmountByInvoiceId(1L)).thenReturn(BigDecimal.ZERO);

        service.delete(10L);

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo("unpaid");
        assertThat(invoice.getPaidInFullBy()).isNull();
    }

    @Test
    void delete_revertsToOverdueWhenPastDue() {
        invoice.setStatus("paid");
        invoice.setAmountPaid(new BigDecimal("1000.00"));
        invoice.setDueAt(LocalDate.now().minusDays(5)); // past due

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setInvoice(invoice);

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.sumAmountByInvoiceId(1L)).thenReturn(BigDecimal.ZERO);

        service.delete(10L);

        assertThat(invoice.getStatus()).isEqualTo("overdue");
    }

    @Test
    void delete_doesNotRevertVoidInvoice() {
        invoice.setStatus("void");

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setInvoice(invoice);

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.sumAmountByInvoiceId(1L)).thenReturn(BigDecimal.ZERO);

        service.delete(10L);

        assertThat(invoice.getStatus()).isEqualTo("void"); // unchanged
    }
}
