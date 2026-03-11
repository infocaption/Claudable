-- ============================================================
-- 028: Background Health Monitor
-- ============================================================
-- Tracks server health state over time so we can detect
-- green→red transitions and push notifications.
-- Supports legacy (.version.xml) and drift (tomcat_hosts) checks.
-- ============================================================

SET NAMES utf8mb4;

-- Persistent health state per server (for transition detection)
CREATE TABLE IF NOT EXISTS server_health_state (
    server_id       INT NOT NULL PRIMARY KEY,
    check_type      ENUM('legacy','drift') NOT NULL DEFAULT 'legacy',
    last_status     VARCHAR(20)  NOT NULL DEFAULT 'unknown',
    last_http_code  INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500) NULL,
    last_checked    TIMESTAMP    NULL,
    changed_at      TIMESTAMP    NULL,
    CONSTRAINT fk_shs_server FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Config keys for background health monitor
INSERT IGNORE INTO app_config (config_key, config_value, category, description, is_secret) VALUES
    ('healthmonitor.enabled',        'true',   'monitoring', 'Bakgrunds-healthcheck aktiverad (true/false)', 0),
    ('healthmonitor.intervalMinutes', '5',     'monitoring', 'Intervall i minuter mellan health-checks', 0),
    ('healthmonitor.checkType',      'legacy', 'monitoring', 'Typ av health-check: legacy (.version.xml), drift (tomcat_hosts), both', 0),
    ('healthmonitor.notifyOnRedOnly','true',   'monitoring', 'Skicka push enbart vid transition till severe/error (true/false)', 0);

INSERT IGNORE INTO schema_version (version, description)
VALUES ('1.8.0', '028 - Background health monitor with push notifications');
