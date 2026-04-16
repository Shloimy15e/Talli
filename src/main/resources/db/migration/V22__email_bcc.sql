-- V22: Track BCC addresses on sent emails for audit trail.
-- Stored as comma-separated list since Email is a log/audit table,
-- not a transactional relationship.

ALTER TABLE emails ADD COLUMN bcc TEXT;
