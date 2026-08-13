package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Payment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ClientService {

    private final ExchangeRateService exchangeRateService;

    public ClientService(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    /** All client financial-summary amounts are normalized to USD. */
    public FinancialSummary financialSummary(List<Invoice> invoices, List<Payment> payments) {
        BigDecimal billed = invoices.stream()
                .filter(i -> !"void".equals(i.getStatus()))
                .map(i -> exchangeRateService.toUsd(
                        i.getAmount(), i.getCurrency(), i.getExchangeRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal collected = payments.stream()
                .filter(p -> p.getAmount() != null && p.getInvoice() != null)
                .map(p -> exchangeRateService.toUsd(
                        p.getAmount(), p.getInvoice().getCurrency(), p.getExchangeRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinancialSummary(billed, collected, totalOutstandingUsd(invoices));
    }

    public BigDecimal totalOutstandingUsd(List<Invoice> invoices) {
        return invoices.stream()
                .filter(Invoice::isOutstanding)
                .map(i -> exchangeRateService.toUsdCurrent(i.balance(), i.getCurrency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * USD aging buckets for collectible invoices. Current rates are used because
     * the amounts represent what could be collected today.
     */
    public AgingBuckets agingUsd(List<Invoice> invoices) {
        LocalDate today = LocalDate.now();
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal days31to60 = BigDecimal.ZERO;
        BigDecimal days61to90 = BigDecimal.ZERO;
        BigDecimal over90 = BigDecimal.ZERO;

        for (Invoice inv : invoices) {
            if (!inv.isOutstanding()) continue;
            BigDecimal balance = exchangeRateService.toUsdCurrent(inv.balance(), inv.getCurrency());

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

    public record FinancialSummary(
            BigDecimal billedUsd,
            BigDecimal collectedUsd,
            BigDecimal outstandingUsd
    ) {}

    public record AgingBuckets(
            BigDecimal current,
            BigDecimal days31to60,
            BigDecimal days61to90,
            BigDecimal over90,
            BigDecimal total
    ) {}
}
