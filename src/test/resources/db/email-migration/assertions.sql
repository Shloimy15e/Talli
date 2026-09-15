\set ON_ERROR_STOP on

DO $$
DECLARE
    wrong_roots INTEGER;
BEGIN
    IF (SELECT count(*) FROM emails) <> 10 THEN
        RAISE EXCEPTION 'expected ten historical emails after migration';
    END IF;

    IF (SELECT array_agg(id ORDER BY id) FROM emails)
       IS DISTINCT FROM ARRAY[1,2,3,4,5,6,7,8,9,10]::BIGINT[] THEN
        RAISE EXCEPTION 'migration changed the historical email id set';
    END IF;

    IF EXISTS (
        SELECT 1 FROM emails
        WHERE message_id IS NOT NULL
           OR in_reply_to IS NOT NULL
           OR references_header IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'migration fabricated RFC header values for historical email';
    END IF;

    SELECT count(*) INTO wrong_roots
    FROM (VALUES
        (1::BIGINT, 1::BIGINT), (2, 1), (3, 1), (4, 4), (5, 5),
        (6, 6), (7, 7), (8, 8), (9, 9), (10, 10)
    ) expected(id, root_id)
    JOIN emails actual USING (id)
    WHERE actual.thread_root_id IS DISTINCT FROM expected.root_id;

    IF wrong_roots <> 0 THEN
        RAISE EXCEPTION 'thread root mismatch count: %', wrong_roots;
    END IF;

    IF (SELECT count(*) FROM email_sender_profiles) <> 5 THEN
        RAISE EXCEPTION 'expected five seeded sender profiles';
    END IF;

    IF (SELECT count(*) FROM email_sender_profiles WHERE active AND is_default) <> 1 THEN
        RAISE EXCEPTION 'expected exactly one active default sender';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM email_sender_profiles
        WHERE address = 'info@dynamiq.dev' AND active AND is_default
    ) THEN
        RAISE EXCEPTION 'info@dynamiq.dev is not the active default';
    END IF;

    IF EXISTS (SELECT 1 FROM email_sender_profiles WHERE signature_html = '') THEN
        RAISE EXCEPTION 'seeded sender signature is blank';
    END IF;

    IF to_regclass('uq_email_sender_profiles_address_ci') IS NULL
       OR to_regclass('uq_email_sender_profiles_active_default') IS NULL
       OR to_regclass('idx_emails_message_id') IS NULL
       OR to_regclass('idx_emails_in_reply_to') IS NULL
       OR to_regclass('idx_emails_thread_root_created') IS NULL THEN
        RAISE EXCEPTION 'one or more migration indexes are missing';
    END IF;

    BEGIN
        INSERT INTO email_sender_profiles(address, name, signature_html, is_default, active)
        VALUES ('INFO@DYNAMIQ.DEV', 'Duplicate', '<p>Duplicate</p>', FALSE, TRUE);
        RAISE EXCEPTION 'case-insensitive address index accepted a duplicate';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO email_sender_profiles(address, name, signature_html, is_default, active)
        VALUES ('other@dynamiq.dev', 'Other', '<p>Other</p>', TRUE, TRUE);
        RAISE EXCEPTION 'active-default index accepted a second default';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;
END $$;

SELECT string_agg(id || ':' || thread_root_id, ', ' ORDER BY id) AS verified_roots
FROM emails;

SELECT count(*) AS profile_count,
       count(*) FILTER (WHERE active) AS active_count,
       max(address) FILTER (WHERE active AND is_default) AS default_address
FROM email_sender_profiles;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'uq_email_sender_profiles_address_ci',
      'uq_email_sender_profiles_active_default',
      'idx_emails_message_id',
      'idx_emails_in_reply_to',
      'idx_emails_thread_root_created'
  )
ORDER BY indexname;
