-- 030: Encryption master key for credential protection
-- Key auto-generated on first use if empty
-- Used by CryptoUtil for AES-256-GCM encryption of stored credentials

INSERT INTO app_config (config_key, config_value, category, description, is_secret)
VALUES ('crypto.masterKey', '', 'security', 'Master key for AES-256-GCM encryption of stored credentials (auto-generated if empty)', 1)
ON DUPLICATE KEY UPDATE config_key = config_key;
