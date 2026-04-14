-- V1: initial schema — clients, projects, rate history
-- Flyway runs this exactly once, then tracks it in flyway_schema_history

CREATE TABLE clients (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    rate_type TEXT NOT NULL CHECK (rate_type IN ('hourly', 'fixed', 'retainer')),
    current_rate DECIMAL(10, 2) NOT NULL,
    billing_frequency TEXT, -- 'weekly', 'biweekly', 'monthly' for hourly; null or 'milestone' for fixed
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'paused', 'completed', 'cancelled')),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE rate_changes (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    old_rate DECIMAL(10, 2),
    new_rate DECIMAL(10, 2) NOT NULL,
    effective_date DATE NOT NULL,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_projects_client_id ON projects(client_id);
CREATE INDEX idx_rate_changes_project_id ON rate_changes(project_id);
