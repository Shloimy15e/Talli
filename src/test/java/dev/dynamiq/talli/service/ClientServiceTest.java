package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Payment;
import dev.dynamiq.talli.service.ClientService.AgingBuckets;
import dev.dynamiq.talli.service.ClientService.FinancialSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientServiceTest {

    private ExchangeRateService exchangeRateService;
    private ClientService service;

    @BeforeEach
    void setUp() {
        exchangeRateService = mock(ExchangeRateService.class);
        when(exchangeRateService.toUsdCurrent(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(exchangeRateService.toUsd(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        service = new ClientService(exchangeRateService);
    }

    private Invoice invoice(String status, BigDecimal amount, BigDecimal paid, LocalDate dueAt) {
        Invoice inv = new Invoice();
        inv.setStatus(status);
        inv.setAmount(amount);
        inv.setAmountPaid(paid);
        inv.setCurrency("USD");
        inv.setExchangeRate(BigDecimal.ONE);
        inv.setDueAt(dueAt);
        return inv;
    }

    @Test
    void aging_currentBucket_invoicesNotYetDue() {
        Invoice inv = invoice("unpaid", new BigDecimal("500"), BigDecimal.ZERO, LocalDate.now().plusDays(10));
        AgingBuckets aging = service.agingUsd(List.of(inv));

        assertThat(aging.current()).isEqualByComparingTo("500.00");
        assertThat(aging.days31to60()).isEqualByComparingTo("0");
        assertThat(aging.over90()).isEqualByComparingTo("0");
        assertThat(aging.total()).isEqualByComparingTo("500.00");
    }

    @Test
    void aging_31to60Bucket() {
        Invoice inv = invoice("overdue", new BigDecimal("300"), BigDecimal.ZERO,
                LocalDate.now().minusDays(45));
        AgingBuckets aging = service.agingUsd(List.of(inv));

        assertThat(aging.current()).isEqualByComparingTo("0");
        assertThat(aging.days31to60()).isEqualByComparingTo("300.00");
        assertThat(aging.total()).isEqualByComparingTo("300.00");
    }

    @Test
    void aging_over90Bucket() {
        Invoice inv = invoice("overdue", new BigDecimal("1000"), new BigDecimal("200"),
                LocalDate.now().minusDays(120));
        AgingBuckets aging = service.agingUsd(List.of(inv));

        assertThat(aging.over90()).isEqualByComparingTo("800.00"); // balance = 1000 - 200
        assertThat(aging.total()).isEqualByComparingTo("800.00");
    }

    @Test
    void aging_excludesPaidAndVoidInvoices() {
        Invoice paid = invoice("paid", new BigDecimal("500"), new BigDecimal("500"), LocalDate.now().minusDays(10));
        Invoice voided = invoice("void", new BigDecimal("300"), BigDecimal.ZERO, LocalDate.now().minusDays(10));
        Invoice unpaid = invoice("unpaid", new BigDecimal("200"), BigDecimal.ZERO, LocalDate.now().plusDays(5));

        AgingBuckets aging = service.agingUsd(List.of(paid, voided, unpaid));

        assertThat(aging.total()).isEqualByComparingTo("200.00");
    }

    @Test
    void aging_multipleBuckets() {
        Invoice current = invoice("unpaid", new BigDecimal("100"), BigDecimal.ZERO, LocalDate.now().plusDays(5));
        Invoice mid = invoice("overdue", new BigDecimal("200"), BigDecimal.ZERO, LocalDate.now().minusDays(50));
        Invoice old = invoice("overdue", new BigDecimal("300"), BigDecimal.ZERO, LocalDate.now().minusDays(100));

        AgingBuckets aging = service.agingUsd(List.of(current, mid, old));

        assertThat(aging.current()).isEqualByComparingTo("100.00");
        assertThat(aging.days31to60()).isEqualByComparingTo("200.00");
        assertThat(aging.over90()).isEqualByComparingTo("300.00");
        assertThat(aging.total()).isEqualByComparingTo("600.00");
    }

    @Test
    void aging_emptyList() {
        AgingBuckets aging = service.agingUsd(List.of());
        assertThat(aging.total()).isEqualByComparingTo("0");
    }

    @Test
    void agingUsd_convertsMixedCurrenciesAndExcludesWrittenOffInvoices() {
        Invoice usd = invoice("unpaid", new BigDecimal("100"), BigDecimal.ZERO,
                LocalDate.now().plusDays(5));
        Invoice ils = invoice("overdue", new BigDecimal("350"), BigDecimal.ZERO,
                LocalDate.now().minusDays(45));
        ils.setCurrency("ILS");
        Invoice writtenOff = invoice("written_off", new BigDecimal("50"), BigDecimal.ZERO,
                LocalDate.now().minusDays(100));
        writtenOff.setAmountWrittenOff(new BigDecimal("50"));

        when(exchangeRateService.toUsdCurrent(new BigDecimal("350"), "ILS"))
                .thenReturn(new BigDecimal("100"));

        AgingBuckets aging = service.agingUsd(List.of(usd, ils, writtenOff));

        assertThat(aging.current()).isEqualByComparingTo("100");
        assertThat(aging.days31to60()).isEqualByComparingTo("100");
        assertThat(aging.total()).isEqualByComparingTo("200");
    }

    @Test
    void financialSummary_usesHistoricRatesForActivityAndCurrentRatesForReceivables() {
        Invoice invoice = invoice("unpaid", new BigDecimal("350"), new BigDecimal("70"),
                LocalDate.now().plusDays(5));
        invoice.setCurrency("ILS");
        invoice.setExchangeRate(new BigDecimal("3.50"));

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(new BigDecimal("70"));
        payment.setExchangeRate(new BigDecimal("3.50"));

        when(exchangeRateService.toUsd(new BigDecimal("350"), "ILS", new BigDecimal("3.50")))
                .thenReturn(new BigDecimal("100"));
        when(exchangeRateService.toUsd(new BigDecimal("70"), "ILS", new BigDecimal("3.50")))
                .thenReturn(new BigDecimal("20"));
        when(exchangeRateService.toUsdCurrent(new BigDecimal("280"), "ILS"))
                .thenReturn(new BigDecimal("80"));

        FinancialSummary summary = service.financialSummary(List.of(invoice), List.of(payment));

        assertThat(summary.billedUsd()).isEqualByComparingTo("100");
        assertThat(summary.collectedUsd()).isEqualByComparingTo("20");
        assertThat(summary.outstandingUsd()).isEqualByComparingTo("80");
    }
}
