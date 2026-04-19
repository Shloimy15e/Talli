-- V24: Project-level billable flag. Non-billable projects = personal/internal work.
-- Time entries on non-billable projects are automatically non-billable.

ALTER TABLE projects ADD COLUMN billable BOOLEAN NOT NULL DEFAULT TRUE;
