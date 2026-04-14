-- V4: time entries — logged against projects

CREATE TABLE time_entries (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,

    -- When the work happened
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,                       -- nullable: if currently running
    duration_minutes INTEGER,                 -- cached on stop for fast reports

    -- What the work was
    description TEXT,

    -- Billing status
    billable BOOLEAN NOT NULL DEFAULT TRUE,   -- not all time is billable
    billed BOOLEAN NOT NULL DEFAULT FALSE,    -- has it been included in an invoice yet
    -- invoice_id added in a later migration once invoices exist

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_time_entries_project_id ON time_entries(project_id);
CREATE INDEX idx_time_entries_started_at ON time_entries(started_at DESC);
CREATE INDEX idx_time_entries_billed ON time_entries(billed) WHERE billable = TRUE;
