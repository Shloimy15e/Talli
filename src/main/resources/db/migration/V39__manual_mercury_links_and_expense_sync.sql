-- Replace the paid-plan invoice API integration with manual payment links and
-- idempotent, read-only expense imports.

ALTER TABLE invoices ADD COLUMN mercury_payment_url TEXT;

UPDATE invoices
SET mercury_payment_url = 'https://app.mercury.com/pay/' || mercury_invoice_slug
WHERE mercury_invoice_slug IS NOT NULL AND mercury_invoice_slug <> '';

DROP INDEX IF EXISTS uq_clients_mercury_customer_id;
ALTER TABLE clients DROP COLUMN IF EXISTS mercury_customer_id;

DROP INDEX IF EXISTS uq_invoices_mercury_invoice_id;
ALTER TABLE invoices
    DROP COLUMN IF EXISTS mercury_invoice_id,
    DROP COLUMN IF EXISTS mercury_invoice_slug,
    DROP COLUMN IF EXISTS mercury_status,
    DROP COLUMN IF EXISTS mercury_synced_at,
    DROP COLUMN IF EXISTS mercury_sync_error;

DROP INDEX IF EXISTS uq_payments_external_reference;
UPDATE payments
SET reference = external_id
WHERE external_provider = 'mercury'
  AND external_id IS NOT NULL
  AND (reference IS NULL OR reference = '');
ALTER TABLE payments
    DROP COLUMN IF EXISTS external_provider,
    DROP COLUMN IF EXISTS external_id;

ALTER TABLE expenses ADD COLUMN mercury_transaction_id VARCHAR(36);
CREATE UNIQUE INDEX uq_expenses_mercury_transaction_id
    ON expenses(mercury_transaction_id);
