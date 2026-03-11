-- 009_app_config.sql
-- Key-value configuration table for externalizing hardcoded settings.
-- Admin can edit values from the admin panel's "Inställningar" tab.

CREATE TABLE IF NOT EXISTS app_config (
    config_key    VARCHAR(100) NOT NULL PRIMARY KEY,
    config_value  TEXT NULL,
    category      VARCHAR(50) NOT NULL DEFAULT 'general',
    description   VARCHAR(500) NULL,
    is_secret     TINYINT(1) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by    INT NULL,
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============ Seed default values ============

-- Database: Local (icdashboard)
INSERT INTO app_config (config_key, config_value, category, description, is_secret) VALUES
('db.url', 'jdbc:mysql://localhost:3306/icdashboard?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Stockholm&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci', 'database', 'JDBC-anslutningssträng för lokal databas', 0),
('db.user', 'CHANGE_ME', 'database', 'Databasanvändare (lokal)', 1),
('db.password', 'CHANGE_ME', 'database', 'Databaslösenord (lokal)', 1)
ON DUPLICATE KEY UPDATE config_key = config_key;

-- Database: External (smartassic)
INSERT INTO app_config (config_key, config_value, category, description, is_secret) VALUES
('db.smartassic.url', 'jdbc:mysql://10.201.21.10:3306/smartassic?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Stockholm&characterEncoding=UTF-8&useUnicode=true', 'database', 'JDBC-anslutningssträng för extern smartassic-databas', 0),
('db.smartassic.user', 'CHANGE_ME', 'database', 'Databasanvändare (smartassic)', 1),
('db.smartassic.password', 'CHANGE_ME', 'database', 'Databaslösenord (smartassic)', 1)
ON DUPLICATE KEY UPDATE config_key = config_key;

-- Azure Communication Services (Email)
INSERT INTO app_config (config_key, config_value, category, description, is_secret) VALUES
('acs.endpoint', 'https://ic-drift-communicationservice.europe.communication.azure.com', 'email', 'Azure Communication Services endpoint URL', 0),
('acs.host', 'ic-drift-communicationservice.europe.communication.azure.com', 'email', 'Azure Communication Services host', 0),
('acs.accessKey', 'CHANGE_ME', 'email', 'Azure Communication Services API-nyckel', 1),
('acs.senderAddress', 'messenger@infocaption.com', 'email', 'Avsändaradress för e-post', 0),
('acs.apiVersion', '2023-03-31', 'email', 'Azure ACS API-version', 0)
ON DUPLICATE KEY UPDATE config_key = config_key;

-- API Security
INSERT INTO app_config (config_key, config_value, category, description, is_secret) VALUES
('api.importKey', 'CHANGE_ME', 'security', 'API-nyckel för import-endpoint (X-API-Key header)', 1)
ON DUPLICATE KEY UPDATE config_key = config_key;

-- Timeouts and Limits
INSERT INTO app_config (config_key, config_value, category, description, is_secret) VALUES
('email.sendTimeout', '30', 'email', 'Timeout i sekunder för e-postutskick', 0),
('email.statusTimeout', '15', 'email', 'Timeout i sekunder för statuskontroll av e-post', 0),
('email.historyLimit', '100', 'email', 'Max antal historikposter att visa', 0),
('contacts.maxResults', '5000', 'general', 'Max antal kontakter att returnera per sökning', 0),
('http.connectTimeout', '30', 'general', 'Timeout i sekunder för HTTP-anslutningar', 0),
('sync.connectTimeout', '15', 'general', 'Timeout i sekunder för synk HTTP-anslutningar', 0),
('server.healthCheckTimeout', '10', 'general', 'Timeout i sekunder för server health-checks', 0),
('server.syncInterval', '10', 'general', 'Intervall i minuter för synk av maskinnamn', 0)
ON DUPLICATE KEY UPDATE config_key = config_key;
