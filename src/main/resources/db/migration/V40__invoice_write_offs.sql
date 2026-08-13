-- Preserve forgiven invoice balances without treating them as payments.

ALTER TABLE invoices ADD COLUMN amount_written_off DECIMAL(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN written_off_at TIMESTAMP;
ALTER TABLE invoices ADD COLUMN write_off_reason TEXT;

ALTER TABLE invoices DROP CONSTRAINT invoices_status_check;
ALTER TABLE invoices ADD CONSTRAINT invoices_status_check
    CHECK (status IN ('unpaid', 'paid', 'overdue', 'written_off', 'void'));
