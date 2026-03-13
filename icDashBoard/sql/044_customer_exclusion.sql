-- 044: Add is_excluded to customers table for organization-level exclusion from stats
-- Mirrors the existing servers.is_excluded pattern but at the organization level.

ALTER TABLE customers ADD COLUMN is_excluded TINYINT(1) NOT NULL DEFAULT 0 AFTER is_active;
CREATE INDEX idx_customer_excluded ON customers (is_excluded);
