-- Track Resend delivery events per email log row.
-- status column stays as our internal send state (pending/sent/failed).
-- Timestamps below are recipient-side states reported via webhook.
ALTER TABLE emails
    ADD COLUMN resend_id VARCHAR(255),
    ADD COLUMN delivered_at TIMESTAMP,
    ADD COLUMN bounced_at TIMESTAMP,
    ADD COLUMN complained_at TIMESTAMP,
    ADD COLUMN opened_at TIMESTAMP,
    ADD COLUMN clicked_at TIMESTAMP,
    ADD COLUMN bounce_reason TEXT;

CREATE INDEX idx_emails_resend_id ON emails(resend_id);
