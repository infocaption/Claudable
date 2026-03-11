-- ============================================================
-- Migration 006: Separate customers (companies) from servers (URLs)
--
-- Before: `customers` = one row per URL, with optional company_id/company_name
-- After:  `customers` = one row per company/organization
--         `servers`   = one row per URL, FK to customers
--         `customer_stats_daily` references server_id instead of customer_id
--
-- Run: "C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < 006_servers_table.sql
-- ============================================================

-- Step 1: Create servers table (copy structure from customers, minus company fields)
CREATE TABLE IF NOT EXISTS servers (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    customer_id     INT          NULL COMMENT 'FK to customers (set after migration)',
    url             VARCHAR(500) NOT NULL,
    url_normalized  VARCHAR(500) NOT NULL,
    current_version VARCHAR(20)  NULL,
    first_seen      DATE         NOT NULL,
    last_seen       DATE         NOT NULL,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_server_url_norm (url_normalized),
    INDEX idx_server_customer (customer_id),
    INDEX idx_server_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 2: Copy all existing customer rows into servers (preserve id for FK continuity)
INSERT INTO servers (id, url, url_normalized, current_version, first_seen, last_seen, is_active, created_at, updated_at)
SELECT id, url, url_normalized, current_version, first_seen, last_seen, is_active, created_at, updated_at
FROM customers;

-- Step 3: Create new company-centric customers table
CREATE TABLE IF NOT EXISTS customers_new (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    company_id      VARCHAR(50)  NULL COMMENT 'SuperOffice company ID',
    company_name    VARCHAR(255) NOT NULL,
    coach_email     VARCHAR(255) NULL COMMENT 'Kundcoach epost (prepared for future use)',
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    first_seen      DATE         NOT NULL,
    last_seen       DATE         NOT NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_customer_company_id (company_id),
    INDEX idx_customer_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 4a: Insert distinct companies (URLs that have company_id)
INSERT INTO customers_new (company_id, company_name, is_active, first_seen, last_seen)
SELECT
    c.company_id,
    MAX(c.company_name),
    MAX(c.is_active),
    MIN(c.first_seen),
    MAX(c.last_seen)
FROM customers c
WHERE c.company_id IS NOT NULL AND c.company_id != ''
GROUP BY c.company_id;

-- Step 4b: Insert auto-created customers for URLs without company_id
-- Uses url_normalized as the company_name (e.g., "markus.infocaption.com")
INSERT INTO customers_new (company_id, company_name, is_active, first_seen, last_seen)
SELECT
    NULL,
    c.url_normalized,
    c.is_active,
    c.first_seen,
    c.last_seen
FROM customers c
WHERE c.company_id IS NULL OR c.company_id = '';

-- Step 5a: Link servers that have company_id to their customer
UPDATE servers s
JOIN customers c_old ON c_old.id = s.id
JOIN customers_new cn ON cn.company_id = c_old.company_id
SET s.customer_id = cn.id
WHERE c_old.company_id IS NOT NULL AND c_old.company_id != '';

-- Step 5b: Link servers without company_id to their auto-created customer
UPDATE servers s
JOIN customers c_old ON c_old.id = s.id
JOIN customers_new cn ON cn.company_name = c_old.url_normalized AND cn.company_id IS NULL
SET s.customer_id = cn.id
WHERE c_old.company_id IS NULL OR c_old.company_id = '';

-- Step 6: Migrate customer_stats_daily FK from customers to servers
-- Drop old FK constraint
ALTER TABLE customer_stats_daily
    DROP FOREIGN KEY fk_stats_customer;

-- Rename column for clarity
ALTER TABLE customer_stats_daily
    CHANGE COLUMN customer_id server_id INT NOT NULL;

-- Drop and recreate unique index with new column name
ALTER TABLE customer_stats_daily
    DROP INDEX idx_customer_date,
    ADD UNIQUE INDEX idx_server_date (server_id, snapshot_date);

-- Add FK to servers
ALTER TABLE customer_stats_daily
    ADD CONSTRAINT fk_stats_server FOREIGN KEY (server_id)
        REFERENCES servers(id) ON DELETE CASCADE;

-- Step 7: Add FK from servers to customers_new
ALTER TABLE servers
    ADD CONSTRAINT fk_server_customer FOREIGN KEY (customer_id)
        REFERENCES customers_new(id) ON DELETE SET NULL;

-- Step 8: Rename tables
RENAME TABLE customers TO customers_old,
             customers_new TO customers;

-- Verification queries (run manually to confirm):
-- SELECT COUNT(*) AS server_count FROM servers;
-- SELECT COUNT(*) AS customer_count FROM customers;
-- SELECT COUNT(*) AS orphan_servers FROM servers WHERE customer_id IS NULL;
-- SELECT s.url_normalized, c.company_name FROM servers s LEFT JOIN customers c ON c.id = s.customer_id LIMIT 10;

-- Step 9 (optional, run after validation):
-- DROP TABLE customers_old;
