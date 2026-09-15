CREATE TABLE email_mailbox_states (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    thread_root_id BIGINT NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
    last_read_email_id BIGINT REFERENCES emails(id) ON DELETE SET NULL,
    archived_through_email_id BIGINT REFERENCES emails(id) ON DELETE SET NULL,
    starred BOOLEAN NOT NULL DEFAULT FALSE,
    marked_unread BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_email_mailbox_states_user_thread UNIQUE (user_id, thread_root_id)
);

CREATE INDEX idx_email_mailbox_states_user_starred
    ON email_mailbox_states(user_id, starred);
CREATE INDEX idx_email_mailbox_states_user_archive
    ON email_mailbox_states(user_id, archived_through_email_id);
