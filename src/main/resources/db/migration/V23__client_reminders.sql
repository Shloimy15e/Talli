-- V23: Automated reminders for unpaid invoices.
-- reminders_enabled: per-client opt-out
-- reminder_interval_days: per-client override for the global default
-- last_reminder_at: when we last sent a reminder (for throttling)

ALTER TABLE clients ADD COLUMN reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE clients ADD COLUMN reminder_interval_days INTEGER;
ALTER TABLE clients ADD COLUMN last_reminder_at TIMESTAMP;
