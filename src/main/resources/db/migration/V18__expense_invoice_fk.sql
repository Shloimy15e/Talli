ALTER TABLE expenses
    ADD COLUMN invoice_id BIGINT NULL REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX idx_expenses_invoice_id ON expenses(invoice_id);
