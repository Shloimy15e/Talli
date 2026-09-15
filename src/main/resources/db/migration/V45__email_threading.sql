-- Preserve RFC email threading metadata so the application can show and reply
-- to the same conversations that recipient mail clients construct.
ALTER TABLE emails
    ADD COLUMN message_id VARCHAR(998),
    ADD COLUMN in_reply_to VARCHAR(998),
    ADD COLUMN references_header TEXT,
    ADD COLUMN thread_root_id BIGINT REFERENCES emails(id) ON DELETE SET NULL;

-- The same RFC message can legitimately arrive as both an outbound message
-- and an inbound copy when a sender delivers to one of our own addresses.
CREATE INDEX idx_emails_message_id ON emails(message_id);
CREATE INDEX idx_emails_in_reply_to ON emails(in_reply_to);
CREATE INDEX idx_emails_thread_root_created ON emails(thread_root_id, created_at, id);

-- Group existing mail once. Runtime conversation reads use only the stored root.
-- A missing historical sender joins a known sender only when that sender is unambiguous.
WITH normalized AS (
    SELECT id,
           NULLIF(lower(regexp_replace(trim(
               CASE WHEN direction = 'in' THEN from_address ELSE to_address END
           ), '^.*<([^<>]+)>[[:space:]]*$', '\1')), '') AS contact,
           NULLIF(lower(regexp_replace(trim(
               CASE WHEN direction = 'in' THEN to_address ELSE from_address END
           ), '^.*<([^<>]+)>[[:space:]]*$', '\1')), '') AS local_sender,
           trim(regexp_replace(
               regexp_replace(lower(trim(subject)), '^(re:[[:space:]]*)+', ''),
               '[[:space:]]+', ' ', 'g'
           )) AS subject
    FROM emails
), known_senders AS (
    SELECT contact, subject,
           count(DISTINCT local_sender) AS sender_count,
           min(local_sender) AS only_sender
    FROM normalized
    GROUP BY contact, subject
), conversation_roots AS (
    SELECT mail.id,
           min(mail.id) OVER (
               PARTITION BY mail.contact, mail.subject,
                   coalesce(mail.local_sender,
                       CASE WHEN known.sender_count = 1 THEN known.only_sender END, ''),
                   CASE WHEN mail.contact IS NULL OR mail.subject = '' THEN mail.id END
           ) AS root_id
    FROM normalized mail
    LEFT JOIN known_senders known
        ON known.contact = mail.contact AND known.subject = mail.subject
)
UPDATE emails
SET thread_root_id = conversation_roots.root_id
FROM conversation_roots
WHERE emails.id = conversation_roots.id;
