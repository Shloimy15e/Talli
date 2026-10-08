ALTER TABLE time_entries ADD COLUMN rate DECIMAL(10, 2);

UPDATE time_entries
SET rate = (SELECT projects.current_rate FROM projects WHERE projects.id = time_entries.project_id);

ALTER TABLE time_entries ALTER COLUMN rate SET NOT NULL;
