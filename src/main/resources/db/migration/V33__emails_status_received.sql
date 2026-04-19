-- Widen the status check constraint so inbound emails can use status='received'.

ALTER TABLE emails DROP CONSTRAINT emails_status_check;
ALTER TABLE emails ADD CONSTRAINT emails_status_check
    CHECK (status IN ('pending', 'sent', 'failed', 'received'));
