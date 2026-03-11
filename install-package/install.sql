-- ============================================================
-- InfoCaption Dashboard — First-Time Installation Script
-- ============================================================
-- This script creates a clean database from scratch.
-- No usage data, no users, no customer/server records.
-- Only structural tables + default seed data (groups, widgets, modules, config).
--
-- Prerequisites:
--   1. MySQL 5.7+ running on localhost:3306
--   2. Database 'icdashboard' created:
--        CREATE DATABASE icdashboard CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   3. User 'icdashboarduser' with full access:
--        CREATE USER 'icdashboarduser'@'localhost' IDENTIFIED BY 'CHANGE_ME';
--        GRANT ALL PRIVILEGES ON icdashboard.* TO 'icdashboarduser'@'localhost';
--        FLUSH PRIVILEGES;
--
-- Run:
--   mysql -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < install.sql
--
-- After running this script:
--   1. Deploy icDashBoard/ to Tomcat webapps
--   2. Start Tomcat — servlets will auto-create remaining tables
--      (customers, servers, customer_stats_daily, email_*, sync_*)
--   3. Open the app and register your first account
--   4. Promote yourself to admin:
--        UPDATE users SET is_admin = 1 WHERE id = 1;
--   5. Configure settings in Admin > Installningar (db passwords, email, etc.)
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;
SET COLLATION_CONNECTION = utf8mb4_unicode_ci;

-- ============================================================
-- 1. Core tables: users, modules
-- ============================================================

CREATE TABLE IF NOT EXISTS `users` (
    `id`                  INT AUTO_INCREMENT PRIMARY KEY,
    `username`            VARCHAR(50)  NOT NULL,
    `email`               VARCHAR(255) NOT NULL,
    `password`            VARCHAR(60)  NOT NULL,
    `full_name`           VARCHAR(100) NOT NULL,
    `profile_picture_url` VARCHAR(500) NULL,
    `created_at`          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_login`          TIMESTAMP    NULL,
    `is_active`           TINYINT(1)   DEFAULT 1,
    `is_admin`            TINYINT(1)   NOT NULL DEFAULT 0,
    UNIQUE KEY `username` (`username`),
    UNIQUE KEY `email` (`email`),
    KEY `idx_username` (`username`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `modules` (
    `id`              INT AUTO_INCREMENT PRIMARY KEY,
    `owner_user_id`   INT          NULL,
    `module_type`     ENUM('system','private','shared') NOT NULL DEFAULT 'private',
    `name`            VARCHAR(100) NOT NULL,
    `icon`            VARCHAR(20)  NOT NULL DEFAULT '?',
    `description`     VARCHAR(500) NULL,
    `category`        VARCHAR(50)  NOT NULL DEFAULT 'tools',
    `entry_file`      VARCHAR(255) NOT NULL DEFAULT 'index.html',
    `directory_name`  VARCHAR(100) NOT NULL,
    `badge`           VARCHAR(50)  NULL,
    `version`         VARCHAR(20)  NOT NULL DEFAULT '1.0',
    `ai_spec_text`    TEXT         NULL,
    `is_active`       TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `directory_name` (`directory_name`),
    KEY `idx_modules_type` (`module_type`),
    KEY `idx_modules_owner` (`owner_user_id`),
    CONSTRAINT `fk_modules_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. Groups, user-groups, module-groups
-- ============================================================

CREATE TABLE IF NOT EXISTS `groups` (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    icon            VARCHAR(20)  NOT NULL DEFAULT '👥',
    description     VARCHAR(500) NULL,
    is_hidden       TINYINT(1)   NOT NULL DEFAULT 0,
    sso_department  VARCHAR(200) NULL DEFAULT NULL,
    created_by      INT          NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_groups_sso_department (sso_department),
    CONSTRAINT fk_groups_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_groups (
    user_id    INT NOT NULL,
    group_id   INT NOT NULL,
    joined_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_ug_user  FOREIGN KEY (user_id)  REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ug_group FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS module_groups (
    module_id  INT NOT NULL,
    group_id   INT NOT NULL,
    PRIMARY KEY (module_id, group_id),
    CONSTRAINT fk_mg_module FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE,
    CONSTRAINT fk_mg_group  FOREIGN KEY (group_id)  REFERENCES `groups`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. Certificates
-- ============================================================

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

-- ============================================================
-- 4. App configuration (key-value store)
-- ============================================================

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

-- ============================================================
-- 5. Widgets
-- ============================================================

CREATE TABLE IF NOT EXISTS widgets (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    icon            VARCHAR(10)   NOT NULL DEFAULT '📦' COMMENT 'Emoji icon',
    description     VARCHAR(500)  NULL,
    render_key      VARCHAR(50)   NOT NULL UNIQUE COMMENT 'Maps to JS renderer function',
    custom_html     TEXT          NULL,
    custom_js       TEXT          NULL,
    refresh_seconds INT           NOT NULL DEFAULT 0,
    is_active       TINYINT       NOT NULL DEFAULT 1,
    created_by      INT           NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS widget_groups (
    widget_id   INT NOT NULL,
    group_id    INT NOT NULL,
    PRIMARY KEY (widget_id, group_id),
    CONSTRAINT fk_wg_widget FOREIGN KEY (widget_id) REFERENCES widgets(id) ON DELETE CASCADE,
    CONSTRAINT fk_wg_group  FOREIGN KEY (group_id)  REFERENCES `groups`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 6. Guide Planner tables
-- ============================================================

CREATE TABLE IF NOT EXISTS guide_projects (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_by  INT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_gp_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS guide_tasks (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    project_id      INT NOT NULL,
    guide_id        VARCHAR(255) NOT NULL DEFAULT '',
    sv_id           VARCHAR(255) NOT NULL DEFAULT '',
    en_id           VARCHAR(255) NOT NULL DEFAULT '',
    no_id           VARCHAR(255) NOT NULL DEFAULT '',
    namn            VARCHAR(500) NOT NULL DEFAULT '',
    description     TEXT NULL,
    task_type       ENUM('new','update') NOT NULL DEFAULT 'update',
    status          ENUM('not_started','in_progress','bumped','skipped','completed') NOT NULL DEFAULT 'not_started',
    assignee        VARCHAR(255) NOT NULL DEFAULT '',
    sort_order      INT NOT NULL DEFAULT 0,
    started_at      TIMESTAMP NULL,
    completed_at    TIMESTAMP NULL,
    completion_time VARCHAR(50) NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_gt_project FOREIGN KEY (project_id) REFERENCES guide_projects(id) ON DELETE CASCADE,
    INDEX idx_gt_project (project_id),
    INDEX idx_gt_guide_id (guide_id),
    INDEX idx_gt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS guide_project_assignees (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    project_id      INT NOT NULL,
    assignee_name   VARCHAR(255) NOT NULL,
    CONSTRAINT fk_gpa_project FOREIGN KEY (project_id) REFERENCES guide_projects(id) ON DELETE CASCADE,
    UNIQUE KEY uq_gpa_project_assignee (project_id, assignee_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 7. Seed: Default groups
-- ============================================================

INSERT IGNORE INTO `groups` (name, icon, description, is_hidden) VALUES
    ('Alla',         '🌐', 'Alla användare — standardgrupp som alla tillhör', 0),
    ('Kundvård',     '💬', 'Kundvårdsteamet', 0),
    ('Utveckling',   '💻', 'Utvecklingsteamet', 0),
    ('Support',      '🎧', 'Supportteamet', 0),
    ('Ledning',      '👔', 'Ledningsgruppen', 1),
    ('IT-säkerhet',  '🔐', 'IT-säkerhetsteamet', 1);

-- ============================================================
-- 8. Seed: Default system modules (10 built-in)
-- ============================================================

INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name, is_active) VALUES
    (NULL, 'system', 'Kundstatistik',        '📊', 'Kundanalys med diagram, Excel-export och trendvisning',                'tools', 'displaystats.html', 'customer-stats',      1),
    (NULL, 'system', 'Utskick',              '📬', 'Skapa och skicka e-postutskick via Azure Communication Services',      'tools', 'index.html',        'utskick',             1),
    (NULL, 'system', 'Trigga Händelser',     '⚡', 'Generera JavaScript-kod för automatisering av klienthändelser',        'tools', 'index.html',        'trigger-builder',     1),
    (NULL, 'system', 'SQL Builder',          '🔧', 'Visuell SQL-frågebyggare',                                            'tools', 'sql-builder.html',  'sql-builder',         1),
    (NULL, 'system', 'Verktygslåda',         '🧰', 'Samling av verktyg och hjälpmedel',                                   'tools', 'toolbox.html',      'toolbox',             1),
    (NULL, 'system', 'Dokumentation',        '📚', 'Dokumentation och kunskapsbank',                                      'tools', 'docs.html',         'docs',                1),
    (NULL, 'system', 'Pong',                 '🏓', 'Pong — klassiskt arkadspel (demomodul)',                               'games', 'pong.html',         'pong',                1),
    (NULL, 'system', 'Guide Planner',        '📋', 'Projektplanering för guideuppdateringar och översättningar',           'tools', 'index.html',        'guide-planner',       1),
    -- Combined drift module (replaces 6 individual modules below)
    (NULL, 'system', 'Drift & Övervakning',  '🖥️', 'Samlad driftvy — infrastruktur, servrar, certifikat, backup, CloudGuard och incidenter', 'admin', 'index.html', 'drift-ops', 1),
    -- Individual drift modules kept for sub-iframe loading, hidden from nav
    (NULL, 'system', 'Serverlista',          '🖥️', 'Översikt av alla servrar med hälsostatus, version och maskinnamn',     'tools', 'index.html',        'server-list',         0),
    (NULL, 'system', 'Certifikat',           '🔒', 'Översikt av SSL-certifikat med utgångsdatum och statusfärger',         'tools', 'index.html',        'certificates',        0),
    (NULL, 'system', 'CloudGuard Monitor',   '🛡️', 'Realtidsövervakning av CloudGuard-incidenter med push-notifikationer','tools', 'index.html',        'cloudguard-monitor',  0),
    (NULL, 'system', 'Drift Övervakning',    '🖥️', 'Infrastrukturöversikt — maskiner, tjänster och Tomcat-hosts',          'admin', 'index.html',        'drift-monitor',       0),
    (NULL, 'system', 'Backup-status',        '💾', 'Övervakning av SQL-backupfiler på alla servrar',                      'admin', 'index.html',        'backup-status',       0),
    (NULL, 'system', 'Incidentrapporter',    '🚨', 'Incidentrapportering och change management',                          'admin', 'index.html',        'incidents',           0)
ON DUPLICATE KEY UPDATE name = VALUES(name), is_active = VALUES(is_active);

-- Assign all system modules to "Alla" group (visible to everyone)
INSERT INTO module_groups (module_id, group_id)
SELECT m.id, g.id FROM modules m, `groups` g
WHERE m.module_type = 'system' AND g.name = 'Alla'
ON DUPLICATE KEY UPDATE module_id = module_id;

-- Seed default Guide Planner project
INSERT INTO guide_projects (name) VALUES ('Projekt 1');

-- ============================================================
-- 9. Seed: Default widgets (7 built-in)
-- ============================================================

INSERT INTO widgets (name, icon, description, render_key) VALUES
    ('Datum & Vecka', '📅', 'Visar dagens datum och veckonummer',           'date_week'),
    ('Klocka',        '🕐', 'Digital klocka med sekunder',                  'clock'),
    ('Serverstatus',  '🖥️', 'Sammanfattning av servrarnas hälsa',          'server_status'),
    ('Certifikat',    '🔒', 'Antal utgångna och kritiska certifikat',       'cert_expiry'),
    ('Kundöversikt',  '📊', 'Antal aktiva servrar och kunder',             'customer_count'),
    ('Snabblänkar',   '🔗', 'Personliga bokmärken och genvägar',           'quick_links'),
    ('Teamöversikt',  '👥', 'Dina grupper och antal medlemmar',            'team_online')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Assign all widgets to "Alla" group
INSERT IGNORE INTO widget_groups (widget_id, group_id)
SELECT w.id, g.id FROM widgets w, `groups` g
WHERE g.name = 'Alla';

-- ============================================================
-- 10. Seed: Default app configuration
--    NOTE: Secret values are set to placeholder 'CHANGE_ME'.
--    Update them via Admin > Installningar after first login.
-- ============================================================

INSERT INTO app_config (config_key, config_value, category, description, is_secret) VALUES
    -- Database: Local
    ('db.url',      'jdbc:mysql://localhost:3306/icdashboard?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Stockholm&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci', 'database', 'JDBC-anslutningssträng för lokal databas', 0),
    ('db.user',     'icdashboarduser', 'database', 'Databasanvändare (lokal)', 1),
    ('db.password', 'CHANGE_ME',       'database', 'Databaslösenord (lokal)', 1),
    -- Database: External (optional)
    ('db.smartassic.url',      'jdbc:mysql://localhost:3306/smartassic?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Stockholm&characterEncoding=UTF-8&useUnicode=true', 'database', 'JDBC-anslutningssträng för extern smartassic-databas', 0),
    ('db.smartassic.user',     'CHANGE_ME', 'database', 'Databasanvändare (smartassic)', 1),
    ('db.smartassic.password', 'CHANGE_ME', 'database', 'Databaslösenord (smartassic)', 1),
    -- Azure Communication Services (Email)
    ('acs.endpoint',       'https://your-acs-resource.europe.communication.azure.com', 'email', 'Azure Communication Services endpoint URL', 0),
    ('acs.host',           'your-acs-resource.europe.communication.azure.com',         'email', 'Azure Communication Services host', 0),
    ('acs.accessKey',      'CHANGE_ME',                'email', 'Azure Communication Services API-nyckel', 1),
    ('acs.senderAddress',  'noreply@yourdomain.com',   'email', 'Avsändaradress för e-post', 0),
    ('acs.apiVersion',     '2023-03-31',               'email', 'Azure ACS API-version', 0),
    -- API Security
    ('api.importKey', 'CHANGE_ME', 'security', 'API-nyckel för import-endpoint (X-API-Key header)', 1),
    -- Auth
    ('auth.loginMethods', 'both', 'security', 'Tillåtna inloggningsmetoder: both, password, sso', 0),
    -- Timeouts and Limits
    ('email.sendTimeout',        '30',   'email',   'Timeout i sekunder för e-postutskick', 0),
    ('email.statusTimeout',      '15',   'email',   'Timeout i sekunder för statuskontroll av e-post', 0),
    ('email.historyLimit',       '100',  'email',   'Max antal historikposter att visa', 0),
    ('contacts.maxResults',      '5000', 'general', 'Max antal kontakter att returnera per sökning', 0),
    ('http.connectTimeout',      '30',   'general', 'Timeout i sekunder för HTTP-anslutningar', 0),
    ('sync.connectTimeout',      '15',   'general', 'Timeout i sekunder för synk HTTP-anslutningar', 0),
    ('server.healthCheckTimeout','10',   'general', 'Timeout i sekunder för server health-checks', 0),
    ('server.syncInterval',      '10',   'general', 'Intervall i minuter för synk av maskinnamn', 0),
    -- SSO Claim URIs (configurable per IdP)
    ('sso.emailClaimUri',        'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress', 'security', 'SAML claim-URI för e-postadress (fallback: NameID)', 0),
    ('sso.givenNameClaimUri',    'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname', 'security', 'SAML claim-URI för förnamn', 0),
    ('sso.surnameClaimUri',      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname', 'security', 'SAML claim-URI för efternamn', 0),
    ('sso.nameClaimUri',         'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name', 'security', 'SAML claim-URI för visningsnamn (fallback 1)', 0),
    ('sso.displayNameClaimUri',  'http://schemas.microsoft.com/identity/claims/displayname', 'security', 'SAML claim-URI för visningsnamn (fallback 2, Microsoft-specifik)', 0),
    -- SSO Group Auto-Assignment
    ('sso.departmentClaimUri',   'department/department', 'security', 'SAML claim-URI för avdelningsattribut (automatisk grupptilldelning)', 0),
    ('sso.autoCreateGroups',     'true', 'security', 'Skapa grupper automatiskt när SSO-avdelning saknar matchande grupp (true/false)', 0),
    ('sso.autoAssignGroups',     'true', 'security', 'Tilldela användare till grupper automatiskt baserat på SSO department-claim (true/false)', 0),
    -- Push Notifications (VAPID keys auto-generated on startup)
    ('vapid.publicKey',          '', 'notifications', 'VAPID public key (Base64url, auto-generated)', 0),
    ('vapid.privateKey',         '', 'notifications', 'VAPID private key (Base64url, auto-generated)', 1),
    ('vapid.subject',            'mailto:admin@infocaption.com', 'notifications', 'VAPID subject (mailto: or URL)', 0),
    ('notifications.enabled',   'true', 'notifications', 'Push-notifikationer aktiverade (true/false)', 0),
    -- Background Health Monitor
    ('healthmonitor.enabled',         'true',   'monitoring', 'Bakgrunds-healthcheck aktiverad (true/false)', 0),
    ('healthmonitor.intervalMinutes', '5',      'monitoring', 'Intervall i minuter mellan health-checks', 0),
    ('healthmonitor.checkType',       'legacy', 'monitoring', 'Typ av health-check: legacy (.version.xml), drift (tomcat_hosts), both', 0),
    ('healthmonitor.notifyOnRedOnly', 'true',   'monitoring', 'Skicka push enbart vid transition till severe/error (true/false)', 0)
ON DUPLICATE KEY UPDATE config_key = config_key;

-- ============================================================
-- 11. Safety: Protect "Alla" group from deletion
-- ============================================================

DROP TRIGGER IF EXISTS trg_prevent_alla_delete;

DELIMITER //
CREATE TRIGGER trg_prevent_alla_delete
BEFORE DELETE ON `groups`
FOR EACH ROW
BEGIN
    IF OLD.name = 'Alla' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Cannot delete the default "Alla" group. This group is required by the system.';
    END IF;
END //
DELIMITER ;

-- ============================================================
-- 12. MCP Gateway
-- ============================================================

CREATE TABLE IF NOT EXISTS mcp_servers (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    transport_type  ENUM('http','stdio') NOT NULL DEFAULT 'http',
    endpoint_url    VARCHAR(1000) NULL,
    command         VARCHAR(500) NULL,
    command_args    TEXT NULL,
    auth_type       ENUM('none','bearer','api_key','basic') NOT NULL DEFAULT 'none',
    auth_config     TEXT NULL,
    tool_prefix     VARCHAR(50) NOT NULL UNIQUE,
    cached_tools    TEXT NULL,
    is_active       TINYINT(1) NOT NULL DEFAULT 1,
    last_connected  TIMESTAMP NULL,
    last_error      TEXT NULL,
    created_by      INT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mcp_audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NULL,
    server_id       INT NULL,
    method          VARCHAR(50) NOT NULL,
    tool_name       VARCHAR(200) NULL,
    request_summary TEXT NULL,
    response_status ENUM('success','error','timeout') NOT NULL,
    error_message   TEXT NULL,
    duration_ms     INT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (server_id) REFERENCES mcp_servers(id) ON DELETE SET NULL,
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_server (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO app_config (config_key, config_value, description, is_secret) VALUES
('mcp.enabled', 'true', 'Global MCP gateway on/off', 0),
('mcp.rateLimit.requestsPerMinute', '60', 'Per-user MCP rate limit (requests/minute)', 0),
('mcp.httpTimeout', '30', 'HTTP upstream MCP timeout (seconds)', 0),
('mcp.stdioTimeout', '30', 'Stdio MCP response timeout (seconds)', 0),
('mcp.stdio.allowedCommands', 'npx,node,python,uv,docker', 'Whitelist of allowed stdio commands', 0),
('mcp.maxResponseSize', '5242880', 'Max MCP response size in bytes (5MB)', 0),
('mcp.auditRetentionDays', '30', 'Auto-purge MCP audit logs older than N days', 0),
('mcp.toolCallMaxArgs', '102400', 'Max tool call arguments size in bytes (100KB)', 0);

-- ============================================================
-- 13. Push Notifications (Web Push)
-- ============================================================

CREATE TABLE IF NOT EXISTS push_subscriptions (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    endpoint        VARCHAR(2000) NOT NULL,
    p256dh_key      VARCHAR(500)  NOT NULL,
    auth_key        VARCHAR(500)  NOT NULL,
    severity_filter VARCHAR(100)  NOT NULL DEFAULT 'critical,error,warning,info',
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pushsub_user (user_id),
    UNIQUE INDEX idx_pushsub_endpoint (endpoint(500)),
    CONSTRAINT fk_pushsub_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT          NULL,
    incident_id     INT          NULL,
    channel         VARCHAR(20)  NOT NULL DEFAULT 'web_push',
    status          ENUM('sent','failed','expired') NOT NULL,
    error_message   TEXT         NULL,
    sent_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notiflog_sent (sent_at),
    INDEX idx_notiflog_user (user_id),
    CONSTRAINT fk_notiflog_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 14. Server Health State (background monitor)
-- ============================================================

CREATE TABLE IF NOT EXISTS server_health_state (
    server_id       INT NOT NULL PRIMARY KEY,
    check_type      ENUM('legacy','drift') NOT NULL DEFAULT 'legacy',
    last_status     VARCHAR(20)  NOT NULL DEFAULT 'unknown',
    last_http_code  INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500) NULL,
    last_checked    TIMESTAMP    NULL,
    changed_at      TIMESTAMP    NULL,
    CONSTRAINT fk_shs_server FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 15. Schema Version Tracking
-- ============================================================

CREATE TABLE IF NOT EXISTS schema_version (
    version       VARCHAR(20)  NOT NULL PRIMARY KEY,
    description   VARCHAR(255) NOT NULL,
    applied_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    applied_by    VARCHAR(100) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO schema_version (version, description)
VALUES ('1.0.0', 'Baseline - nyinstallation');

-- ============================================================
-- Done! Deploy icDashBoard/ to Tomcat and register your first user.
-- Promote to admin: UPDATE users SET is_admin = 1 WHERE id = 1;
-- ============================================================
