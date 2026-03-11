-- Migration 018: API token authentication system
-- Adds per-user API tokens with configurable TTL for script/automation access.

-- 1. Add has_api_access flag to users
ALTER TABLE users ADD COLUMN has_api_access TINYINT(1) NOT NULL DEFAULT 0 AFTER is_admin;

-- 2. Create api_tokens table
CREATE TABLE IF NOT EXISTS api_tokens (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    token_prefix VARCHAR(8) NOT NULL,
    name        VARCHAR(100) NULL,
    expires_at  TIMESTAMP NOT NULL,
    last_used   TIMESTAMP NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active   TINYINT(1) DEFAULT 1,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_token_hash (token_hash),
    INDEX idx_token_user (user_id),
    INDEX idx_token_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Config keys for token TTL
INSERT INTO app_config (config_key, config_value, category, description, is_secret)
VALUES
    ('api.tokenTtlDays', '60', 'security', 'API token livslängd i dagar (standard 60)', 0),
    ('api.maxTokensPerUser', '5', 'security', 'Max antal aktiva API-tokens per användare', 0)
ON DUPLICATE KEY UPDATE config_key = config_key;

-- 4. Give existing admin users API access by default
UPDATE users SET has_api_access = 1 WHERE is_admin = 1;
