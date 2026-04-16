-- V25: link payments to credits when they come from a deposit application.
-- source = 'direct' (fresh cash) or 'credit' (applied from client_credits).

ALTER TABLE payments ADD COLUMN source TEXT NOT NULL DEFAULT 'direct'
    CHECK (source IN ('direct', 'credit'));

ALTER TABLE payments ADD COLUMN credit_id BIGINT
    REFERENCES client_credits(id) ON DELETE RESTRICT;

CREATE INDEX idx_payments_credit_id ON payments(credit_id);
