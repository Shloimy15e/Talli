ALTER TABLE clients ADD COLUMN default_hourly_rate DECIMAL(10,2);
ALTER TABLE clients ADD CONSTRAINT clients_default_hourly_rate_nonnegative
    CHECK (default_hourly_rate >= 0);
