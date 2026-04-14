-- V5: expenses + subscriptions — business outgoings

CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,

    -- Optional tagging
    client_id BIGINT REFERENCES clients(id) ON DELETE SET NULL,
    project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,

    -- What it is
    vendor TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL CHECK (category IN (
        'software', 'hardware', 'travel', 'meals',
        'contractors', 'office', 'marketing', 'taxes', 'other'
    )),

    -- How much & how often
    amount DECIMAL(10, 2) NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    cycle TEXT NOT NULL CHECK (cycle IN ('monthly', 'yearly')),

    -- Lifecycle
    started_on DATE NOT NULL,
    cancelled_on DATE,
    next_due_on DATE,

    -- Management links
    manage_url TEXT,
    cancel_url TEXT,

    -- Payment
    payment_method TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE expenses (
    id BIGSERIAL PRIMARY KEY,

    -- Optional tagging
    client_id BIGINT REFERENCES clients(id) ON DELETE SET NULL,
    project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    subscription_id BIGINT REFERENCES subscriptions(id) ON DELETE SET NULL,

    -- What & when
    incurred_on DATE NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    category TEXT NOT NULL CHECK (category IN (
        'software', 'hardware', 'travel', 'meals',
        'contractors', 'office', 'marketing', 'taxes', 'other'
    )),
    vendor TEXT,
    description TEXT,
    payment_method TEXT,
    receipt_url TEXT,

    -- Rebilling (pass-through to client invoice, optional)
    billable BOOLEAN NOT NULL DEFAULT FALSE,
    billed BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_active ON subscriptions(cancelled_on) WHERE cancelled_on IS NULL;
CREATE INDEX idx_subscriptions_next_due ON subscriptions(next_due_on) WHERE cancelled_on IS NULL;
CREATE INDEX idx_subscriptions_client_id ON subscriptions(client_id);
CREATE INDEX idx_subscriptions_project_id ON subscriptions(project_id);

CREATE INDEX idx_expenses_incurred_on ON expenses(incurred_on DESC);
CREATE INDEX idx_expenses_client_id ON expenses(client_id);
CREATE INDEX idx_expenses_project_id ON expenses(project_id);
CREATE INDEX idx_expenses_subscription_id ON expenses(subscription_id);
CREATE INDEX idx_expenses_category ON expenses(category);
