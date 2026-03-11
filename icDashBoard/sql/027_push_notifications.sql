-- ============================================================
-- 027: Push Notifications (Web Push API)
-- ============================================================
-- Browser push subscriptions + notification logging for
-- CloudGuard incidents. Uses VAPID keys stored in app_config.
-- ============================================================

SET NAMES utf8mb4;

-- Push subscriptions (one per user per browser)
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    endpoint        VARCHAR(2000) NOT NULL,
    p256dh_key      VARCHAR(500)  NOT NULL,
    auth_key        VARCHAR(500)  NOT NULL,
    severity_filter VARCHAR(100)  NOT NULL DEFAULT 'critical,error,warning,info',
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pushsub_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_pushsub_user (user_id),
    UNIQUE INDEX idx_pushsub_endpoint (endpoint(500))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Notification send log
CREATE TABLE IF NOT EXISTS notification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT          NULL,
    incident_id     INT          NULL,
    channel         VARCHAR(20)  NOT NULL DEFAULT 'web_push',
    status          ENUM('sent','failed','expired') NOT NULL,
    error_message   TEXT         NULL,
    sent_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notiflog_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_notiflog_sent (sent_at),
    INDEX idx_notiflog_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- VAPID keys + notification toggle (generated on first startup)
INSERT IGNORE INTO app_config (config_key, config_value, category, description, is_secret) VALUES
    ('vapid.publicKey',        '', 'notifications', 'VAPID public key (Base64url, auto-generated)', 0),
    ('vapid.privateKey',       '', 'notifications', 'VAPID private key (Base64url, auto-generated)', 1),
    ('vapid.subject',          'mailto:admin@infocaption.com', 'notifications', 'VAPID subject (mailto: or URL)', 0),
    ('notifications.enabled',  'true', 'notifications', 'Push-notifikationer aktiverade (true/false)', 0);

INSERT IGNORE INTO schema_version (version, description)
VALUES ('1.7.0', '027 - Push notifications for CloudGuard incidents');
