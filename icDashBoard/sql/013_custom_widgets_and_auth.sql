-- Migration 013: Custom widgets columns + auth login method config
-- Run with: mysql -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < sql/013_custom_widgets_and_auth.sql

-- Add custom HTML/JS support to widgets table
ALTER TABLE widgets ADD COLUMN IF NOT EXISTS custom_html TEXT NULL AFTER render_key;
ALTER TABLE widgets ADD COLUMN IF NOT EXISTS custom_js TEXT NULL AFTER custom_html;
ALTER TABLE widgets ADD COLUMN IF NOT EXISTS refresh_seconds INT NOT NULL DEFAULT 0 AFTER custom_js;
ALTER TABLE widgets ADD COLUMN IF NOT EXISTS created_by INT NULL AFTER is_active;

-- Add auth login method configuration
INSERT INTO app_config (config_key, config_value, category, description, is_secret)
VALUES ('auth.loginMethods', 'both', 'security', 'Tillåtna inloggningsmetoder: both (lösenord + SSO), password (bara lösenord), sso (bara SAML2 SSO — auto-redirect)', 0)
ON DUPLICATE KEY UPDATE config_key = config_key;
