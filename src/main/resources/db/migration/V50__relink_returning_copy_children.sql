-- V49 classified exact returning copies and moved the copy row itself. This
-- follow-up preserves real replies that were previously rooted at that copy.
-- V49 is already deployed, so this must remain a separate, idempotent repair.
UPDATE emails child
SET thread_root_id = COALESCE(canonical.thread_root_id, canonical.id)
FROM emails copy
JOIN emails canonical ON canonical.id = copy.copy_of_email_id
WHERE child.thread_root_id = copy.id
  AND child.copy_of_email_id IS NULL;
