-- 031: Security audit log for admin actions and authentication events

CREATE TABLE IF NOT EXISTS audit_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type  VARCHAR(50) NOT NULL COMMENT 'login_success, login_failed, admin_action, config_change, user_delete, token_create, etc.',
  user_id     INT NULL,
  username    VARCHAR(255) NULL COMMENT 'For failed logins where user_id is unknown',
  ip_address  VARCHAR(50) NULL,
  target_type VARCHAR(50) NULL COMMENT 'user, group, module, config, sync_config, token',
  target_id   VARCHAR(100) NULL,
  details     TEXT NULL COMMENT 'Human-readable description of the action',
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_type (event_type),
  INDEX idx_audit_user (user_id),
  INDEX idx_audit_time (created_at),
  INDEX idx_audit_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Retention config
INSERT INTO app_config (config_key, config_value, category, description, is_secret)
VALUES ('audit.retentionDays', '90', 'security', 'Number of days to keep audit log entries', 0)
ON DUPLICATE KEY UPDATE config_key = config_key;
