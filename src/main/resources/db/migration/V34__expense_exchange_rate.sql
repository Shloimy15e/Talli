-- Lock each expense to its historic USD exchange rate at the time it was incurred,
-- mirroring the pattern used for invoices/payments (V20). Defaults to 1.0 so
-- USD rows are correct on insert; non-USD legacy rows are backfilled on boot
-- by ExpenseExchangeRateBackfill (they will have rate=1.0 until then).

ALTER TABLE expenses ADD COLUMN exchange_rate DECIMAL(10, 6) NOT NULL DEFAULT 1.0;
