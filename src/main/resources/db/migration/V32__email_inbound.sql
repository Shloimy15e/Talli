-- Phase 4: support inbound emails received via Resend's inbound webhook.

ALTER TABLE emails ADD COLUMN direction VARCHAR(8) NOT NULL DEFAULT 'out';
ALTER TABLE emails ADD COLUMN from_address VARCHAR(255);
ALTER TABLE emails ADD COLUMN received_at TIMESTAMP;

CREATE INDEX idx_emails_direction ON emails(direction);
