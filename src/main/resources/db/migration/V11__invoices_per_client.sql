-- V11: move invoices from per-project to per-client.
-- Invoices now belong to a client and aggregate work across their projects.
-- Each invoice_item carries an optional project_id pointing at the source project
-- (hourly time aggregate, fixed milestone, retainer fee). Nullable so pass-through
-- expenses, discounts, or other non-project lines can still live on an invoice.

-- Add client_id nullable, backfill from the existing project_id → project.client_id,
-- then enforce NOT NULL.
ALTER TABLE invoices
    ADD COLUMN client_id BIGINT REFERENCES clients(id) ON DELETE RESTRICT;

UPDATE invoices
SET client_id = projects.client_id
FROM projects
WHERE invoices.project_id = projects.id;

ALTER TABLE invoices
    ALTER COLUMN client_id SET NOT NULL;

ALTER TABLE invoices
    DROP COLUMN project_id;

ALTER TABLE invoice_items
    ADD COLUMN project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL;

DROP INDEX IF EXISTS idx_invoices_project_id;
CREATE INDEX idx_invoices_client_id ON invoices(client_id);
CREATE INDEX idx_invoice_items_project_id ON invoice_items(project_id);
