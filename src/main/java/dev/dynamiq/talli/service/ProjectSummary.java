package dev.dynamiq.talli.service;

import java.math.BigDecimal;

public record ProjectSummary(
    BigDecimal billedToDate,     // sum of invoice_items.total for this project
    BigDecimal unbilledValue,    // hourly only: sum(hours × rate) of eligible entries
    BigDecimal remaining,        // fixed: contract - billed; else null
    BigDecimal monthBilled,      // retainer: this calendar month's billed; else null
    long entryCount,
    long invoiceCount
) {}

