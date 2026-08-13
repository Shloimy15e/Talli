-- Preserve visible CC recipients on outbound email audit records.
ALTER TABLE emails ADD COLUMN cc TEXT;
