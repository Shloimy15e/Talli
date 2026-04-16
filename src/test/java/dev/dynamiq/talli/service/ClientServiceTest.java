package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.service.ClientService.AgingBuckets;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientServiceTest {

    private final ClientService service = new ClientService();

    private Invoice invoice(String status, BigDecimal amount, BigDecimal paid, LocalDate dueAt) {
        Invoice inv = new Invoice();
        inv.setStatus(status);
        inv.setAmount(amount);
        inv.setAmountPaid(paid);
        inv.setDueAt(dueAt);
        return inv;
    }

    @Test
    void aging_currentBucket_invoicesNotYetDue() {
        Invoice inv = invoice("unpaid", new BigDecimal("500"), BigDecimal.ZERO, LocalDate.now().plusDays(10));
        AgingBuckets aging = service.aging(List.of(inv));

        assertThat(aging.current()).isEqualByComparingTo("500.00");
        assertThat(aging.days31to60()).isEqualByComparingTo("0");
        assertThat(aging.over90()).isEqualByComparingTo("0");
        assertThat(aging.total()).isEqualByComparingTo("500.00");
    }

    @Test
    void aging_31to60Bucket() {
        Invoice inv = invoice("overdue", new BigDecimal("300"), BigDecimal.ZERO,
                LocalDate.now().minusDays(45));
        AgingBuckets aging = service.aging(List.of(inv));

        assertThat(aging.current()).isEqualByComparingTo("0");
        assertThat(aging.days31to60()).isEqualByComparingTo("300.00");
        assertThat(aging.total()).isEqualByComparingTo("300.00");
    }

    @Test
    void aging_over90Bucket() {
        Invoice inv = invoice("overdue", new BigDecimal("1000"), new BigDecimal("200"),
                LocalDate.now().minusDays(120));
        AgingBuckets aging = service.aging(List.of(inv));

        assertThat(aging.over90()).isEqualByComparingTo("800.00"); // balance = 1000 - 200
        assertThat(aging.total()).isEqualByComparingTo("800.00");
    }

    @Test
    void aging_excludesPaidAndVoidInvoices() {
        Invoice paid = invoice("paid", new BigDecimal("500"), new BigDecimal("500"), LocalDate.now().minusDays(10));
        Invoice voided = invoice("void", new BigDecimal("300"), BigDecimal.ZERO, LocalDate.now().minusDays(10));
        Invoice unpaid = invoice("unpaid", new BigDecimal("200"), BigDecimal.ZERO, LocalDate.now().plusDays(5));

        AgingBuckets aging = service.aging(List.of(paid, voided, unpaid));

        assertThat(aging.total()).isEqualByComparingTo("200.00");
    }

    @Test
    void aging_multipleBuckets() {
        Invoice current = invoice("unpaid", new BigDecimal("100"), BigDecimal.ZERO, LocalDate.now().plusDays(5));
        Invoice mid = invoice("overdue", new BigDecimal("200"), BigDecimal.ZERO, LocalDate.now().minusDays(50));
        Invoice old = invoice("overdue", new BigDecimal("300"), BigDecimal.ZERO, LocalDate.now().minusDays(100));

        AgingBuckets aging = service.aging(List.of(current, mid, old));

        assertThat(aging.current()).isEqualByComparingTo("100.00");
        assertThat(aging.days31to60()).isEqualByComparingTo("200.00");
        assertThat(aging.over90()).isEqualByComparingTo("300.00");
        assertThat(aging.total()).isEqualByComparingTo("600.00");
    }

    @Test
    void aging_emptyList() {
        AgingBuckets aging = service.aging(List.of());
        assertThat(aging.total()).isEqualByComparingTo("0");
    }
}
