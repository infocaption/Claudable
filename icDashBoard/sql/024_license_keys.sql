-- 024_license_keys.sql
-- License keys table + SuperOffice sync config
-- Track data (train/assist/map) is per-license in the SuperOffice API, NOT per-customer.

-- 1. Create license_keys table (1:N relation to servers)
--    Actual SuperOffice getLicenses API fields:
--    licenseKeyID, keyName, url, holder, train (0/1), assist (0/1), map (0/1)
CREATE TABLE IF NOT EXISTS license_keys (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    server_id       INT NULL,
    license_key_id  VARCHAR(50) NOT NULL COMMENT 'SuperOffice licenseKeyID',
    filename        VARCHAR(255) NULL COMMENT 'keyName (license key name)',
    server_url      VARCHAR(500) NULL COMMENT 'url (raw URL from SuperOffice)',
    server_version  VARCHAR(50) NULL COMMENT 'Not in current API - reserved',
    license_holder  VARCHAR(255) NULL COMMENT 'holder (license holder name)',
    expiration_date VARCHAR(50) NULL COMMENT 'Not in current API - reserved',
    train           TINYINT(1) DEFAULT 0 COMMENT 'Train track enabled (0/1)',
    assist          TINYINT(1) DEFAULT 0 COMMENT 'Assist track enabled (0/1)',
    map             TINYINT(1) DEFAULT 0 COMMENT 'Map track enabled (0/1)',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_license_key_id (license_key_id),
    INDEX idx_license_server (server_id),
    CONSTRAINT fk_license_server FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Sync config: SuperOffice → Licenser
--    Fetches license keys from SuperOffice getLicenses endpoint.
--    json_root_path = 'licenses' (API returns {"licenses": [...]})
--    Upserts by licenseKeyID → license_key_id, resolves server_id via URL normalization.
INSERT INTO sync_configs (name, source_url, auth_type, json_root_path, target_table,
    id_field_source, id_field_target, field_mappings, schedule_minutes, update_only, created_by)
VALUES (
    'SuperOffice → Licenser',
    'https://online2.superoffice.com/Cust16404/CS/scripts/customer.fcgi?action=safeParse&includeId=getLicenses&key=9FCZfc37CNTRfBuQ',
    'none',
    'licenses',
    'license_keys',
    'licenseKeyID',
    'license_key_id',
    '[{"source":"keyName","target":"filename"},{"source":"url","target":"server_url"},{"source":"holder","target":"license_holder"},{"source":"train","target":"train"},{"source":"assist","target":"assist"},{"source":"map","target":"map"},{"source":"url|url_normalize","target":"server_id","lookup":"servers.url_normalized"}]',
    60,
    0,
    (SELECT id FROM users WHERE is_admin = 1 ORDER BY id LIMIT 1)
);
