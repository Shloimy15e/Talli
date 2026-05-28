ALTER TABLE projects
    ADD COLUMN website_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN website_public_url VARCHAR(500),
    ADD COLUMN website_type VARCHAR(80) NOT NULL DEFAULT 'northlight_json_v1',
    ADD COLUMN github_owner VARCHAR(120),
    ADD COLUMN github_repo VARCHAR(120),
    ADD COLUMN github_branch VARCHAR(120),
    ADD COLUMN github_installation_id BIGINT,
    ADD COLUMN last_publish_sha VARCHAR(80),
    ADD COLUMN last_publish_at TIMESTAMP;
