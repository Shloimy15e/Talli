CREATE TABLE email_sender_profiles (
    id BIGSERIAL PRIMARY KEY,
    address VARCHAR(320) NOT NULL,
    name VARCHAR(200) NOT NULL,
    signature_html TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_email_sender_profiles_address_ci
    ON email_sender_profiles (lower(address));

CREATE UNIQUE INDEX uq_email_sender_profiles_active_default
    ON email_sender_profiles (is_default)
    WHERE is_default = TRUE AND active = TRUE;

INSERT INTO email_sender_profiles (address, name, signature_html, is_default, active) VALUES
    ('info@dynamiq.dev', 'Dynamiq Solutions', '<strong>Dynamiq Solutions</strong><br><a href="mailto:info@dynamiq.dev">info@dynamiq.dev</a>', TRUE, TRUE),
    ('billing@dynamiq.dev', 'Dynamiq Billing', '<strong>Dynamiq Billing</strong><br><a href="mailto:billing@dynamiq.dev">billing@dynamiq.dev</a>', FALSE, TRUE),
    ('finance@dynamiq.dev', 'Dynamiq Finance', '<strong>Dynamiq Finance</strong><br><a href="mailto:finance@dynamiq.dev">finance@dynamiq.dev</a>', FALSE, TRUE),
    ('support@dynamiq.dev', 'Dynamiq Support', '<strong>Dynamiq Support</strong><br><a href="mailto:support@dynamiq.dev">support@dynamiq.dev</a>', FALSE, TRUE),
    ('sales@dynamiq.dev', 'Dynamiq Sales', '<strong>Dynamiq Sales</strong><br><a href="mailto:sales@dynamiq.dev">sales@dynamiq.dev</a>', FALSE, TRUE);
