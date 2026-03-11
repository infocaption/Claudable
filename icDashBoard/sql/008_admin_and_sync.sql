-- Migration 008: Admin role + Data sync configuration
-- Run: "C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < 008_admin_and_sync.sql

-- Step 1: Add is_admin column to users table
ALTER TABLE users ADD COLUMN is_admin TINYINT(1) NOT NULL DEFAULT 0 AFTER is_active;

-- Step 2: Set initial admin (Zid)
UPDATE users SET is_admin = 1 WHERE email = 'zid@infocaption.com';

-- Step 3: Create sync_configs table
CREATE TABLE IF NOT EXISTS sync_configs (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    source_url        VARCHAR(2000) NOT NULL,
    auth_type         ENUM('none', 'api_key', 'bearer', 'basic') NOT NULL DEFAULT 'none',
    auth_config       TEXT NULL COMMENT 'JSON: {headerName, keyValue, token, username, password}',
    json_root_path    VARCHAR(500) NULL COMMENT 'Dot-path to array, e.g. data.users',
    target_table      VARCHAR(100) NOT NULL,
    id_field_source   VARCHAR(255) NOT NULL COMMENT 'JSON field name for upsert match',
    id_field_target   VARCHAR(255) NOT NULL COMMENT 'DB column name for upsert match',
    field_mappings    TEXT NOT NULL COMMENT 'JSON array: [{"source":"x","target":"y"}, ...]',
    schedule_minutes  INT NOT NULL DEFAULT 0 COMMENT '0=manual only, >0=auto interval in minutes',
    is_active         TINYINT(1) NOT NULL DEFAULT 1,
    last_run_at       TIMESTAMP NULL,
    last_run_status   VARCHAR(50) NULL,
    last_run_count    INT NULL DEFAULT 0,
    created_by        INT NOT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sync_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 4: Create sync_run_history table
CREATE TABLE IF NOT EXISTS sync_run_history (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_id         INT NOT NULL,
    started_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at      TIMESTAMP NULL,
    status            ENUM('running', 'success', 'failed') DEFAULT 'running',
    records_fetched   INT DEFAULT 0,
    records_upserted  INT DEFAULT 0,
    records_failed    INT DEFAULT 0,
    error_message     TEXT NULL,
    triggered_by      INT NULL COMMENT 'user_id if manual, NULL if scheduled',
    CONSTRAINT fk_synchistory_config FOREIGN KEY (config_id) REFERENCES sync_configs(id) ON DELETE CASCADE,
    CONSTRAINT fk_synchistory_user FOREIGN KEY (triggered_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
