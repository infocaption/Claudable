-- Migration 036: Tomcat Manager integration configs
-- Stores credentials and settings for automatic sync via Tomcat Manager API

CREATE TABLE IF NOT EXISTS tomcat_manager_configs (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    service_id      INT NOT NULL,                    -- FK → machine_services (Tomcat service)
    manager_url     VARCHAR(500) NOT NULL,            -- https://host:port/manager/text/list
    username        VARCHAR(255) NOT NULL,
    password        VARCHAR(500) NOT NULL,            -- Encrypted via CryptoUtil
    is_enabled      TINYINT(1) DEFAULT 1,
    sync_interval   INT DEFAULT 10,                   -- minutes between syncs
    health_endpoint VARCHAR(500) DEFAULT '/.version.xml',
    last_sync_at    TIMESTAMP NULL,
    last_sync_status ENUM('ok','error','never') DEFAULT 'never',
    last_sync_message VARCHAR(500) NULL,
    last_sync_hosts  INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (service_id) REFERENCES machine_services(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_tmc_service (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add source column to tomcat_hosts to distinguish manual vs auto-discovered
ALTER TABLE tomcat_hosts ADD COLUMN source ENUM('manual','manager') DEFAULT 'manual' AFTER notes;
-- Ignore error 1060 (column already exists) silently in code

-- App config keys for Tomcat Manager sync
INSERT INTO app_config (config_key, config_value, category, is_secret, description)
VALUES
    ('drift.managerSync.intervalMinutes', '10', 'drift', 0, 'Minutes between Tomcat Manager host syncs'),
    ('drift.managerSync.healthIntervalMinutes', '5', 'drift', 0, 'Minutes between Tomcat Manager health checks'),
    ('drift.managerSync.httpTimeoutSeconds', '15', 'drift', 0, 'HTTP timeout for Tomcat Manager API calls'),
    ('drift.managerSync.healthTimeoutSeconds', '10', 'drift', 0, 'HTTP timeout for health endpoint checks')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
