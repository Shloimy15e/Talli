-- V13: optional billing identity fields on the client.
-- Surfaced on invoice "Bill To" block. All nullable — not every client needs them.

ALTER TABLE clients
    ADD COLUMN billing_address TEXT,
    ADD COLUMN phone VARCHAR(40),
    ADD COLUMN tax_id VARCHAR(60);
