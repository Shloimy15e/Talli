CREATE TABLE payments (
    id            BIGSERIAL PRIMARY KEY,
    invoice_id    BIGINT       NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    paid_at       DATE         NOT NULL,
    amount        NUMERIC(14,2) NOT NULL,
    method        VARCHAR(30),                       -- zelle / check / ach / wire / cash / other
    reference     VARCHAR(120),                      -- check #, txn id, etc.
    notes         TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_invoice_id ON payments(invoice_id);
CREATE INDEX idx_payments_paid_at ON payments(paid_at);
