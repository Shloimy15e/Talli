ALTER TABLE emails
    ADD COLUMN invoice_id BIGINT NULL REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX idx_emails_invoice_id ON emails(invoice_id);
