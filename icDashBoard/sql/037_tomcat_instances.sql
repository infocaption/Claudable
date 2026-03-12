-- Migration 037: Tomcat Instances module
-- Stores local Tomcat install paths for server.xml parsing and app health checks

CREATE TABLE IF NOT EXISTS tomcat_instances (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    install_path    VARCHAR(500) NOT NULL,
    is_active       TINYINT(1) DEFAULT 1,
    last_scan_at    TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_ti_path (install_path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Register module
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name, is_active)
VALUES (NULL, 'system', 'Tomcat Manager', '🐱', 'Hantera lokala Tomcat-installationer — läs server.xml, se deployade appar och kontrollera hälsostatus', 'admin', 'index.html', 'tomcat-manager', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);
