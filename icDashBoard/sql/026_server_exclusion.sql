-- Migration 026: Add is_excluded flag to servers table
-- Allows admins to exclude specific servers (e.g. beta, test) from customer statistics

ALTER TABLE servers
ADD COLUMN is_excluded TINYINT(1) NOT NULL DEFAULT 0 AFTER is_active,
ADD INDEX idx_server_excluded (is_excluded);
