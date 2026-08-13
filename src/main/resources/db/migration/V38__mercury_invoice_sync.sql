-- Link local clients/invoices/payments to Mercury without changing the existing
-- invoice and payment lifecycle semantics.

ALTER TABLE clients
    ADD COLUMN mercury_customer_id VARCHAR(36);

CREATE UNIQUE INDEX uq_clients_mercury_customer_id
    ON clients(mercury_customer_id);

ALTER TABLE invoices
    ADD COLUMN mercury_invoice_id VARCHAR(36),
    ADD COLUMN mercury_invoice_slug TEXT,
    ADD COLUMN mercury_status VARCHAR(30),
    ADD COLUMN mercury_synced_at TIMESTAMP,
    ADD COLUMN mercury_sync_error TEXT;

CREATE UNIQUE INDEX uq_invoices_mercury_invoice_id
    ON invoices(mercury_invoice_id);

ALTER TABLE payments
    ADD COLUMN external_provider VARCHAR(30),
    ADD COLUMN external_id VARCHAR(120);

CREATE UNIQUE INDEX uq_payments_external_reference
    ON payments(external_provider, external_id);
