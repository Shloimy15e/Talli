-- Keep inbound copies of locally sent messages for audit, but do not surface
-- them as new incoming mail. Matching remains exact on the RFC Message-ID.
ALTER TABLE emails
    ADD COLUMN copy_of_email_id BIGINT REFERENCES emails(id) ON DELETE SET NULL;

CREATE INDEX idx_emails_copy_of_email_id ON emails(copy_of_email_id);

-- Backfill only unambiguous exact Message-ID matches. The canonical outbound
-- row is the earliest local send carrying that identifier.
WITH canonical_outgoing AS (
    SELECT message_id, MIN(id) AS email_id
    FROM emails
    WHERE direction = 'out'
      AND message_id IS NOT NULL
    GROUP BY message_id
)
UPDATE emails copy
SET copy_of_email_id = outgoing.id,
    thread_root_id = COALESCE(outgoing.thread_root_id, outgoing.id)
FROM canonical_outgoing canonical
JOIN emails outgoing ON outgoing.id = canonical.email_id
WHERE copy.direction = 'in'
  AND copy.copy_of_email_id IS NULL
  AND copy.message_id = canonical.message_id;
