ALTER TABLE users
    ADD COLUMN invite_token VARCHAR(80) UNIQUE,
    ADD COLUMN invite_sent_at TIMESTAMP;
