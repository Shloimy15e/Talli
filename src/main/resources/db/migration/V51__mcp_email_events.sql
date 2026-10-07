CREATE TABLE mcp_event_subscriptions (
    id VARCHAR(68) PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient TEXT NOT NULL,
    callback_url TEXT NOT NULL,
    signing_secret TEXT NOT NULL,
    previous_secret TEXT,
    rotation_until TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX mcp_event_subscriptions_recipient ON mcp_event_subscriptions(recipient) WHERE active;
CREATE TABLE mcp_event_outbox (
    id BIGSERIAL PRIMARY KEY,
    subscription_id VARCHAR(68) NOT NULL REFERENCES mcp_event_subscriptions(id) ON DELETE CASCADE,
    event_id VARCHAR(68) NOT NULL,
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    last_status INTEGER,
    UNIQUE(subscription_id, event_id)
);
CREATE INDEX mcp_event_outbox_due ON mcp_event_outbox(next_attempt_at) WHERE status IN ('pending', 'processing');
