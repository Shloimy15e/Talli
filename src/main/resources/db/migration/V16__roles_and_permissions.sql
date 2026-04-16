-- Spatie-style roles + permissions: DB entities with many-to-many pivots.

CREATE TABLE roles (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE permissions (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE role_has_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_has_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Seed the four roles.
INSERT INTO roles (name) VALUES ('admin'), ('bookkeeper'), ('accountant'), ('client');

-- Seed permissions.
INSERT INTO permissions (name) VALUES
    ('view-dashboard'),
    ('manage-clients'), ('view-clients'),
    ('manage-projects'), ('view-projects'),
    ('manage-time'), ('view-time'),
    ('manage-expenses'), ('view-expenses'),
    ('manage-invoices'), ('view-invoices'),
    ('manage-payments'), ('view-payments'),
    ('send-emails'),
    ('manage-users'),
    ('view-reports'),
    ('portal-access');

-- Assign permissions to roles.
-- admin: everything
INSERT INTO role_has_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'admin';

-- bookkeeper: financial CRUD + view the rest
INSERT INTO role_has_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.name = 'bookkeeper'
      AND p.name IN (
          'view-dashboard', 'manage-clients', 'view-clients',
          'view-projects', 'view-time',
          'manage-expenses', 'view-expenses',
          'manage-invoices', 'view-invoices',
          'manage-payments', 'view-payments',
          'send-emails', 'view-reports'
      );

-- accountant: read-only financial
INSERT INTO role_has_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.name = 'accountant'
      AND p.name IN (
          'view-dashboard', 'view-clients',
          'view-projects', 'view-time',
          'view-expenses', 'view-invoices',
          'view-payments', 'view-reports'
      );

-- client: portal only
INSERT INTO role_has_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.name = 'client'
      AND p.name = 'portal-access';

-- Migrate existing users: map old role string to new pivot.
INSERT INTO user_has_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE lower(u.role) = r.name;

-- Drop the old role column.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users DROP COLUMN role;
