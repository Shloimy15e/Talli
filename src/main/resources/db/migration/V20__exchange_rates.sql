-- V20: Exchange rates for currency conversion.
-- Stores rate to convert invoice/payment currency to USD (base currency).
-- e.g., ILS with rate 3.5 means 1 USD = 3.5 ILS, so ILS amount / 3.5 = USD.
-- USD records default to 1.0.

ALTER TABLE invoices ADD COLUMN exchange_rate DECIMAL(10, 6) NOT NULL DEFAULT 1.0;
ALTER TABLE payments ADD COLUMN exchange_rate DECIMAL(10, 6) NOT NULL DEFAULT 1.0;
