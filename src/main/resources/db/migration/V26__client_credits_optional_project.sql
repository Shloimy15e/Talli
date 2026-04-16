-- V26: optionally scope a credit to a project.
-- Client-scoped by default; project-scoped when the deposit is for a specific engagement.
-- Keeping nullable matches how QuickBooks/Xero do it while still supporting
-- PSA-style project-deposit workflows (Bonsai/HoneyBook).

ALTER TABLE client_credits ADD COLUMN project_id BIGINT
    REFERENCES projects(id) ON DELETE SET NULL;

CREATE INDEX idx_client_credits_project_id ON client_credits(project_id);
