-- V8: media — polymorphic file attachments (Spatie-style).
-- Any entity that implements HasMedia can attach files via MediaService.

CREATE TABLE media (
    id BIGSERIAL PRIMARY KEY,

    -- Polymorphic owner. owner_type is a short alias (e.g. "expense", "invoice"),
    -- resolved in application code, not a FK — the entity knows its own alias.
    owner_type TEXT NOT NULL,
    owner_id BIGINT NOT NULL,

    -- Named group within an owner (e.g. "receipts", "logo", "default").
    collection_name TEXT NOT NULL DEFAULT 'default',

    -- File metadata
    filename TEXT NOT NULL,      -- original filename from the upload
    disk_key TEXT NOT NULL,      -- path/key on the storage backend
    mime_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_owner ON media(owner_type, owner_id);
CREATE INDEX idx_media_owner_collection ON media(owner_type, owner_id, collection_name);
