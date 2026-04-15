-- V12: payment terms on clients; projects gain media-attachability.
-- Payment terms default to Net 30 (US B2B services standard).
-- Projects implement HasMedia — SOW contracts stored via MediaService under
-- the "sow" collection. No schema change needed for the media side; the
-- polymorphic media table already supports any owner_type.

ALTER TABLE clients
    ADD COLUMN payment_terms_days INT NOT NULL DEFAULT 30;
