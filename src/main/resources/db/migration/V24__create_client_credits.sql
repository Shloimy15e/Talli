-- V24: client credits — deposits and pre-payments held against future invoices.
-- Cash received that is NOT earned revenue yet; recognized when applied to an invoice.

CREATE TABLE client_credits (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    amount DECIMAL(12, 2) NOT NULL CHECK (amount > 0),
    currency TEXT NOT NULL,
    description TEXT,
    received_at DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_client_credits_client_id ON client_credits(client_id);
CREATE INDEX idx_client_credits_received_at ON client_credits(received_at DESC);
