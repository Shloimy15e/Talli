-- Phase 3: support HTML email bodies and per-user HTML signatures.

ALTER TABLE users ADD COLUMN signature TEXT;

ALTER TABLE emails ADD COLUMN body_html TEXT;
