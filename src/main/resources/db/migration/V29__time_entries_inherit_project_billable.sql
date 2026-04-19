-- V29: Backfill non-billable time entries from their non-billable project.
-- Anything tracked against a project that's now marked non-billable should not
-- be invoiceable. Historic `billable=true` entries on those projects are reset.

UPDATE time_entries te
SET billable = FALSE
FROM projects p
WHERE te.project_id = p.id
  AND p.billable = FALSE
  AND te.billable = TRUE;
