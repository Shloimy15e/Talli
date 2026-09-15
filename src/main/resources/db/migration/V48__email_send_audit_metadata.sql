-- Attribute application-originated email without manufacturing history for existing rows.
ALTER TABLE emails
    ADD COLUMN send_source VARCHAR(32),
    ADD COLUMN initiated_by VARCHAR(255);
