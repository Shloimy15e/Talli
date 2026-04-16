package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Invoice;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ClientService {

    /**
     * Aging buckets for outstanding invoices: current (0-30 days), 31-60, 61-90, 90+.
     * Each bucket sums the balance() of unpaid/overdue invoices whose dueAt falls
     * in that range. Invoices with no dueAt are treated as current.
     */
    public AgingBuckets aging(List<Invoice> invoices) {
        LocalDate today = LocalDate.now();
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal days31to60 = BigDecimal.ZERO;
        BigDecimal days61to90 = BigDecimal.ZERO;
        BigDecimal over90 = BigDecimal.ZERO;

        for (Invoice inv : invoices) {
            if (!"unpaid".equals(inv.getStatus()) && !"overdue".equals(inv.getStatus())) continue;
            BigDecimal balance = inv.balance();
            if (balance.signum() <= 0) continue;

            long daysOverdue = inv.getDueAt() != null
                    ? ChronoUnit.DAYS.between(inv.getDueAt(), today)
                    : 0;

            if (daysOverdue <= 0) {
                current = current.add(balance);
            } else if (daysOverdue <= 30) {
                current = current.add(balance);  // 0-30 days past due = still "current" bucket
            } else if (daysOverdue <= 60) {
                days31to60 = days31to60.add(balance);
            } else if (daysOverdue <= 90) {
                days61to90 = days61to90.add(balance);
            } else {
                over90 = over90.add(balance);
            }
        }

        return new AgingBuckets(current, days31to60, days61to90, over90,
                current.add(days31to60).add(days61to90).add(over90));
    }

    public record AgingBuckets(
            BigDecimal current,
            BigDecimal days31to60,
            BigDecimal days61to90,
            BigDecimal over90,
            BigDecimal total
    ) {}
}
