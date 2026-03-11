-- 021_backup_status.sql
-- Backup file monitoring table + module registration
-- Tracks SQL backup files on each Tomcat server (size, last modified, status)

CREATE TABLE IF NOT EXISTS backup_status (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    server_ip       VARCHAR(50)   NOT NULL,
    file_path       VARCHAR(500)  NOT NULL,
    file_name       VARCHAR(255)  NOT NULL,
    file_size_mb    DECIMAL(10,2) NULL COMMENT 'File size in MB, NULL if missing',
    last_modified   DATETIME      NULL COMMENT 'File last write time, NULL if missing',
    file_exists     TINYINT(1)    NOT NULL DEFAULT 0,
    status          ENUM('ok','warning','critical') NOT NULL DEFAULT 'critical',
    status_reason   VARCHAR(255)  NULL COMMENT 'Why this status was assigned',
    last_scanned    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_backup_server_file (server_ip, file_path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Register module
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name)
VALUES (NULL, 'system', 'Backup-status', '💾', 'Övervakning av SQL-backupfiler på alla servrar', 'admin', 'index.html', 'backup-status')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Assign to Alla group
INSERT IGNORE INTO module_groups (module_id, group_id)
SELECT m.id, g.id FROM modules m, `groups` g
WHERE m.directory_name = 'backup-status' AND g.name = 'Alla';
