\set ON_ERROR_STOP on

CREATE TABLE emails (
    id BIGINT PRIMARY KEY,
    direction VARCHAR(3),
    from_address VARCHAR(320),
    to_address VARCHAR(320),
    subject VARCHAR(255),
    created_at TIMESTAMP
);

INSERT INTO emails(id, direction, from_address, to_address, subject, created_at) VALUES
    (1, 'out', NULL, 'alice@example.com', 'Project update', CURRENT_TIMESTAMP),
    (2, 'in', 'Alice <ALICE@example.com>', 'info@dynamiq.dev', 'Re: Project update', CURRENT_TIMESTAMP),
    (3, 'out', 'info@dynamiq.dev', 'alice@example.com', '  Re: RE: project   update ', CURRENT_TIMESTAMP),
    (4, 'out', 'info@dynamiq.dev', 'bob@example.com', 'Project update', CURRENT_TIMESTAMP),
    (5, 'out', 'info@dynamiq.dev', 'alice@example.com', 'Invoice', CURRENT_TIMESTAMP),
    (6, 'out', 'info@dynamiq.dev', 'carol@example.com', 'Question', CURRENT_TIMESTAMP),
    (7, 'in', 'carol@example.com', 'billing@dynamiq.dev', 'Re: Question', CURRENT_TIMESTAMP),
    (8, 'out', NULL, 'carol@example.com', 'Question', CURRENT_TIMESTAMP),
    (9, 'in', NULL, 'info@dynamiq.dev', 'Notice', CURRENT_TIMESTAMP),
    (10, 'in', NULL, 'info@dynamiq.dev', 'Notice', CURRENT_TIMESTAMP);
