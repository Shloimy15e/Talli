-- V9: allow disk_key to be null temporarily during media creation.
-- MediaService saves the row first to get an id, then sets the real disk_key
-- ({id}/{filename}) before storage.store writes the bytes. Within a @Transactional
-- method this is atomic to outside observers, but the column needs to accept NULL
-- during the intermediate state.

ALTER TABLE media ALTER COLUMN disk_key DROP NOT NULL;
