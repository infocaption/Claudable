-- 015_user_preferences.sql
-- Creates user_preferences table for storing per-user settings (widget config, UI preferences, etc.)
-- Replaces localStorage-based storage with server-side persistence.

CREATE TABLE IF NOT EXISTS user_preferences (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    pref_key    VARCHAR(100) NOT NULL,
    pref_value  TEXT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_pref (user_id, pref_key),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
