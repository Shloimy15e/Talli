-- V10: invoices, invoice_items, and link invoices to time_entries.

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,

    reference TEXT NOT NULL UNIQUE,

    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,

    amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    amount_paid DECIMAL(12, 2) NOT NULL DEFAULT 0,
    currency TEXT NOT NULL DEFAULT 'USD',

    status TEXT NOT NULL DEFAULT 'unpaid' CHECK (status IN ('unpaid', 'paid', 'overdue', 'void')),

    notes TEXT,

    -- Billing window covered by this invoice (day-granular).
    period_start DATE,
    period_end DATE,

    -- Lifecycle dates.
    issued_at DATE NOT NULL,
    due_at DATE,
    sent_at TIMESTAMP,
    paid_in_full_by TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_project_id ON invoices(project_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_issued_at ON invoices(issued_at DESC);


CREATE TABLE invoice_items (
    id BIGSERIAL PRIMARY KEY,

    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,

    description TEXT,
    unit TEXT,
    unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0,
    unit_count DECIMAL(12, 2) NOT NULL DEFAULT 1,
    total DECIMAL(12, 2) NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_items_invoice_id ON invoice_items(invoice_id);


-- Link time entries to the invoice (and specific line item) they were billed on.
-- NULL = not yet invoiced. Both kept separate so deleting a line can null just invoice_item_id
-- without losing which invoice the entry was attached to.
ALTER TABLE time_entries
    ADD COLUMN invoice_id BIGINT REFERENCES invoices(id) ON DELETE SET NULL,
    ADD COLUMN invoice_item_id BIGINT REFERENCES invoice_items(id) ON DELETE SET NULL;

CREATE INDEX idx_time_entries_invoice_id ON time_entries(invoice_id);
CREATE INDEX idx_time_entries_invoice_item_id ON time_entries(invoice_item_id);
