-- Bank and payment-provider transaction IDs make agent-recorded payments
-- idempotent without affecting existing manually recorded payments.

ALTER TABLE payments ADD COLUMN external_provider VARCHAR(60);
ALTER TABLE payments ADD COLUMN external_id VARCHAR(255);

CREATE UNIQUE INDEX uq_payments_external_provider_id
    ON payments(external_provider, external_id);
