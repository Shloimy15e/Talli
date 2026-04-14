-- V3: add currency to projects (ISO 4217 codes: USD, ILS, EUR, GBP, etc.)
ALTER TABLE projects ADD COLUMN currency TEXT NOT NULL DEFAULT 'USD';