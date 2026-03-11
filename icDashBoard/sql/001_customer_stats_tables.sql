-- ============================================================
-- Migration: Customer Statistics Historical Storage
-- Database: icdashboard
-- Run: mysql -u icdashboarduser -p icdashboard < 001_customer_stats_tables.sql
-- ============================================================

-- Customers lookup table (dimension)
-- Stores unique customers identified by normalized URL
CREATE TABLE IF NOT EXISTS customers (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    url             VARCHAR(500) NOT NULL,
    url_normalized  VARCHAR(500) NOT NULL,
    company_id      VARCHAR(50)  NULL COMMENT 'SuperOffice company ID',
    company_name    VARCHAR(255) NULL COMMENT 'SuperOffice company name',
    current_version VARCHAR(20)  NULL,
    first_seen      DATE         NOT NULL,
    last_seen       DATE         NOT NULL,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_url_norm (url_normalized),
    INDEX idx_company (company_id),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Daily statistics snapshots (fact table)
-- One row per customer per day, stores all metrics as captured by getstats.ps1
CREATE TABLE IF NOT EXISTS customer_stats_daily (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id                 INT         NOT NULL,
    snapshot_date               DATE        NOT NULL,
    publiseringar_30d           INT         NOT NULL DEFAULT 0,
    publiseringar_30_60d        INT         NOT NULL DEFAULT 0,
    skapade_guider_30d          INT         NOT NULL DEFAULT 0,
    skapade_guider_30_60d       INT         NOT NULL DEFAULT 0,
    visningar_30d               INT         NOT NULL DEFAULT 0,
    visningar_30_60d            INT         NOT NULL DEFAULT 0,
    processvisningar_30d        INT         NOT NULL DEFAULT 0,
    processvisningar_30_60d     INT         NOT NULL DEFAULT 0,
    processer_skapade_30d       INT         NOT NULL DEFAULT 0,
    processer_skapade_30_60d    INT         NOT NULL DEFAULT 0,
    antal_producenter           INT         NOT NULL DEFAULT 0,
    antal_administratorer       INT         NOT NULL DEFAULT 0,
    totalt_antal_anvandare      INT         NOT NULL DEFAULT 0,
    antal_aktiva_producenter_6m INT         NOT NULL DEFAULT 0,
    version                     VARCHAR(20) NULL,
    created_at                  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_customer_date (customer_id, snapshot_date),
    INDEX idx_date (snapshot_date),
    CONSTRAINT fk_stats_customer FOREIGN KEY (customer_id)
        REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
