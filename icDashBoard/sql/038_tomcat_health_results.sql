-- 038: Tomcat health results + instance meta columns
-- Adds background health check storage and instance metadata

-- Meta columns on tomcat_instances
ALTER TABLE tomcat_instances
  ADD COLUMN machine_name VARCHAR(255) NULL AFTER name,
  ADD COLUMN environment ENUM('production','staging','test','development') DEFAULT 'production' AFTER machine_name,
  ADD COLUMN description VARCHAR(500) NULL AFTER environment,
  ADD COLUMN last_health_at TIMESTAMP NULL AFTER last_scan_at,
  ADD COLUMN health_ok INT DEFAULT 0 AFTER last_health_at,
  ADD COLUMN health_warn INT DEFAULT 0 AFTER health_ok,
  ADD COLUMN health_error INT DEFAULT 0 AFTER health_warn;

-- Health check results table
CREATE TABLE IF NOT EXISTS tomcat_health_results (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    instance_id     INT NOT NULL,
    target_name     VARCHAR(255) NOT NULL,
    target_host     VARCHAR(255) NOT NULL,
    target_url      VARCHAR(500) NOT NULL,
    context_path    VARCHAR(255) DEFAULT '/',
    doc_base        VARCHAR(500) NULL,
    status          ENUM('ok','warning','error') DEFAULT 'error',
    status_code     INT DEFAULT -1,
    error_message   VARCHAR(500) NULL,
    checked_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE,
    INDEX idx_thr_instance (instance_id),
    INDEX idx_thr_checked (checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Config keys for health check scheduler
INSERT INTO app_config (config_key, config_value, category, is_secret, description) VALUES
    ('tomcat.healthInterval', '5', 'tomcat', 0, 'Health check interval in minutes'),
    ('tomcat.healthTimeout', '5', 'tomcat', 0, 'Health check HTTP timeout in seconds')
ON DUPLICATE KEY UPDATE config_key = config_key;
