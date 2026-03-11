-- 011_certificates_table.sql
-- Creates certificates table for SSL certificate tracking
-- and registers the Certifikat module

CREATE TABLE IF NOT EXISTS certificates (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    server_ip         VARCHAR(50)  NOT NULL COMMENT 'Tomcat server IP',
    keystore_path     VARCHAR(500) NOT NULL COMMENT 'Original local path to keystore file',
    hostname_pattern  VARCHAR(255) NULL COMMENT 'SSLHostConfig hostName e.g. *.infocaption.com',
    subject           VARCHAR(500) NULL COMMENT 'Certificate subject (CN=...)',
    issuer            VARCHAR(500) NULL COMMENT 'Certificate issuer',
    serial_number     VARCHAR(100) NULL COMMENT 'Certificate serial number',
    valid_from        DATE         NOT NULL,
    valid_to          DATE         NOT NULL,
    last_checked      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_cert_server_keystore (server_ip, keystore_path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Register Certifikat module
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name)
VALUES (NULL, 'system', 'Certifikat', '🔒', 'Översikt av SSL-certifikat med utgångsdatum och statusfärger', 'tools', 'index.html', 'certificates')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Assign to Drift group (same pattern as server-list module)
INSERT IGNORE INTO module_groups (module_id, group_id)
SELECT m.id, g.id FROM modules m, `groups` g
WHERE m.directory_name = 'certificates' AND g.name = 'Drift';
