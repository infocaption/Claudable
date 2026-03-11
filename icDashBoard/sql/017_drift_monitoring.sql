-- 017_drift_monitoring.sql
-- Drift Övervakning: Infrastructure inventory for Windows machines,
-- services (Tomcat, MySQL, Java) and Tomcat hosts (customer sites).

-- ─── Machines (physical/virtual Windows servers) ───
CREATE TABLE IF NOT EXISTS machines (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    hostname        VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(50) NULL,
    os_name         VARCHAR(100) NULL,
    os_version      VARCHAR(100) NULL,
    cpu             VARCHAR(200) NULL,
    ram_gb          INT NULL,
    disk_info       VARCHAR(500) NULL,
    environment     ENUM('production','staging','test','development') DEFAULT 'production',
    location        VARCHAR(100) NULL,
    notes           TEXT NULL,
    is_active       TINYINT(1) DEFAULT 1,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_machine_hostname (hostname),
    INDEX idx_machine_ip (ip_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─── Services on machines (Java, MySQL, Tomcat, etc.) ───
CREATE TABLE IF NOT EXISTS machine_services (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    machine_id      INT NOT NULL,
    service_type    ENUM('tomcat','mysql','java','iis','other') NOT NULL,
    service_name    VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NULL,
    version         VARCHAR(100) NULL,
    install_path    VARCHAR(500) NULL,
    port            INT NULL,
    ssl_port        INT NULL,
    status          ENUM('running','stopped','disabled','unknown') DEFAULT 'unknown',
    startup_type    ENUM('automatic','manual','disabled','unknown') DEFAULT 'unknown',
    last_status_check TIMESTAMP NULL,
    notes           TEXT NULL,
    is_active       TINYINT(1) DEFAULT 1,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (machine_id) REFERENCES machines(id) ON DELETE CASCADE,
    INDEX idx_service_machine (machine_id),
    INDEX idx_service_type (service_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─── Tomcat hosts (sites/apps per Tomcat instance) ───
CREATE TABLE IF NOT EXISTS tomcat_hosts (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    service_id      INT NOT NULL,
    server_id       INT NULL,
    customer_id     INT NULL,
    hostname        VARCHAR(255) NOT NULL,
    context_path    VARCHAR(100) DEFAULT '/InfoCaptionCore',
    app_version     VARCHAR(50) NULL,
    db_name         VARCHAR(100) NULL,
    db_service_id   INT NULL,
    health_status   ENUM('ok','warning','error','unknown') DEFAULT 'unknown',
    health_message  VARCHAR(500) NULL,
    health_checked_at TIMESTAMP NULL,
    status          ENUM('active','inactive','maintenance','decommissioned') DEFAULT 'active',
    notes           TEXT NULL,
    is_active       TINYINT(1) DEFAULT 1,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (service_id) REFERENCES machine_services(id) ON DELETE CASCADE,
    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE SET NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
    FOREIGN KEY (db_service_id) REFERENCES machine_services(id) ON DELETE SET NULL,
    INDEX idx_host_service (service_id),
    INDEX idx_host_server (server_id),
    INDEX idx_host_customer (customer_id),
    INDEX idx_host_health (health_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─── Register module ───
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name, is_active)
VALUES (NULL, 'system', 'Drift Övervakning', '🖥️', 'Infrastrukturöversikt — maskiner, tjänster och Tomcat-hosts', 'admin', 'index.html', 'drift-monitor', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Assign to "Alla" group (visible to everyone)
INSERT IGNORE INTO module_groups (module_id, group_id)
SELECT m.id, g.id FROM modules m, `groups` g
WHERE m.directory_name = 'drift-monitor' AND g.name = 'Alla';


-- ════════════════════════════════════════════════════════
-- SEED DATA — 4 machines, services, and ~20 hosts
-- ════════════════════════════════════════════════════════

-- ─── Machines ───
INSERT INTO machines (hostname, ip_address, os_name, os_version, cpu, ram_gb, environment, location) VALUES
('IC-PROD-01', '10.201.21.8',  'Windows Server 2022', '21H2 (Build 20348)', 'Intel Xeon E5-2680 v4 (4 vCPU)', 16, 'production', 'Azure Sweden Central'),
('IC-PROD-02', '10.201.21.10', 'Windows Server 2022', '21H2 (Build 20348)', 'Intel Xeon E5-2680 v4 (8 vCPU)', 32, 'production', 'Azure Sweden Central'),
('IC-PROD-03', '10.201.21.12', 'Windows Server 2019', '1809 (Build 17763)',  'Intel Xeon E5-2680 v4 (4 vCPU)', 16, 'production', 'Azure Sweden Central'),
('IC-TEST-01', '10.201.21.20', 'Windows Server 2022', '21H2 (Build 20348)', 'Intel Xeon E5-2680 v4 (2 vCPU)',  8, 'test',       'Azure Sweden Central');

-- ─── Services: IC-PROD-01 ───
INSERT INTO machine_services (machine_id, service_type, service_name, display_name, version, install_path, port, ssl_port, status, startup_type) VALUES
((SELECT id FROM machines WHERE hostname='IC-PROD-01'), 'java',   'N/A',           'Eclipse Adoptium JDK',   '21.0.10',  'C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.10.7-hotspot', NULL, NULL, 'running', 'automatic'),
((SELECT id FROM machines WHERE hostname='IC-PROD-01'), 'mysql',  'MySQL57',       'MySQL Server 5.7',       '5.7.44',   'C:\\Program Files\\MySQL\\MySQL Server 5.7',                 3306, NULL, 'running', 'automatic'),
((SELECT id FROM machines WHERE hostname='IC-PROD-01'), 'tomcat', 'Tomcat9_IC',    'Apache Tomcat 9.0 (IC)', '9.0.100',  'C:\\Program Files\\Apache Software Foundation\\Tomcat 9.0',   8080, 8443, 'running', 'automatic');

-- ─── Services: IC-PROD-02 ───
INSERT INTO machine_services (machine_id, service_type, service_name, display_name, version, install_path, port, ssl_port, status, startup_type) VALUES
((SELECT id FROM machines WHERE hostname='IC-PROD-02'), 'java',   'N/A',           'Eclipse Adoptium JDK',   '21.0.10',  'C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.10.7-hotspot', NULL, NULL, 'running', 'automatic'),
((SELECT id FROM machines WHERE hostname='IC-PROD-02'), 'mysql',  'MySQL57',       'MySQL Server 5.7',       '5.7.44',   'C:\\Program Files\\MySQL\\MySQL Server 5.7',                 3306, NULL, 'running', 'automatic'),
((SELECT id FROM machines WHERE hostname='IC-PROD-02'), 'tomcat', 'Tomcat9_Main',  'Apache Tomcat 9.0 (Main)', '9.0.100', 'C:\\Tomcat9_Main',                                           8080, 8443, 'running', 'automatic'),
((SELECT id FROM machines WHERE hostname='IC-PROD-02'), 'tomcat', 'Tomcat9_Extra', 'Apache Tomcat 9.0 (Extra)', '9.0.85', 'C:\\Tomcat9_Extra',                                          8090, 8444, 'running', 'automatic');

-- ─── Services: IC-PROD-03 ───
INSERT INTO machine_services (machine_id, service_type, service_name, display_name, version, install_path, port, ssl_port, status, startup_type) VALUES
((SELECT id FROM machines WHERE hostname='IC-PROD-03'), 'java',   'N/A',           'Eclipse Adoptium JDK',   '21.0.10',  'C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.10.7-hotspot', NULL, NULL, 'running', 'automatic'),
((SELECT id FROM machines WHERE hostname='IC-PROD-03'), 'mysql',  'MySQL57',       'MySQL Server 5.7',       '5.7.44',   'C:\\Program Files\\MySQL\\MySQL Server 5.7',                 3306, NULL, 'running', 'automatic'),
((SELECT id FROM machines WHERE hostname='IC-PROD-03'), 'tomcat', 'Tomcat9_IC',    'Apache Tomcat 9.0 (IC)', '9.0.100',  'C:\\Program Files\\Apache Software Foundation\\Tomcat 9.0',   8080, 8443, 'stopped', 'automatic');

-- ─── Services: IC-TEST-01 ───
INSERT INTO machine_services (machine_id, service_type, service_name, display_name, version, install_path, port, ssl_port, status, startup_type) VALUES
((SELECT id FROM machines WHERE hostname='IC-TEST-01'), 'java',   'N/A',           'Eclipse Adoptium JDK',   '21.0.10',  'C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.10.7-hotspot', NULL, NULL, 'running', 'automatic'),
((SELECT id FROM machines WHERE hostname='IC-TEST-01'), 'mysql',  'MySQL57',       'MySQL Server 5.7',       '5.7.44',   'C:\\Program Files\\MySQL\\MySQL Server 5.7',                 3306, NULL, 'running', 'automatic'),
((SELECT id FROM machines WHERE hostname='IC-TEST-01'), 'tomcat', 'Tomcat9_Test',  'Apache Tomcat 9.0 (Test)', '9.0.100', 'C:\\Tomcat9_Test',                                           8080, 8443, 'running', 'automatic');


-- ─── Tomcat Hosts: IC-PROD-01 (5 hosts) ───
INSERT INTO tomcat_hosts (service_id, hostname, app_version, db_name, db_service_id, health_status, status) VALUES
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_name='Tomcat9_IC'),
 'markus.infocaption.com', '5.100', 'markusdb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_name='Tomcat9_IC'),
 'anna.infocaption.com', '5.100', 'annadb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_name='Tomcat9_IC'),
 'erik.infocaption.com', '5.99', 'erikdb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_name='Tomcat9_IC'),
 'sofia.infocaption.com', '5.99', 'sofiadb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_type='mysql'),
 'warning', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_name='Tomcat9_IC'),
 'demo1.infocaption.com', '5.98', 'demo1db',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-01' AND ms.service_type='mysql'),
 'ok', 'active');

-- ─── Tomcat Hosts: IC-PROD-02 Main (7 hosts) ───
INSERT INTO tomcat_hosts (service_id, hostname, app_version, db_name, db_service_id, health_status, status) VALUES
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Main'),
 'karin.infocaption.com', '5.100', 'karindb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Main'),
 'lars.infocaption.com', '5.100', 'larsdb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Main'),
 'nina.infocaption.com', '5.99', 'ninadb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Main'),
 'oscar.infocaption.com', '5.99', 'oscardb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'error', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Main'),
 'petra.infocaption.com', '5.100', 'petradb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Main'),
 'robin.infocaption.com', '5.98', 'robindb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Main'),
 'sara.infocaption.com', '5.100', 'saradb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'ok', 'active');

-- ─── Tomcat Hosts: IC-PROD-02 Extra (3 hosts — older Tomcat) ───
INSERT INTO tomcat_hosts (service_id, hostname, app_version, db_name, db_service_id, health_status, status) VALUES
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Extra'),
 'legacy1.infocaption.com', '5.98', 'legacy1db',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Extra'),
 'legacy2.infocaption.com', '5.98', 'legacy2db',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'warning', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_name='Tomcat9_Extra'),
 'legacy3.infocaption.com', '5.98', 'legacy3db',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-02' AND ms.service_type='mysql'),
 'ok', 'inactive');

-- ─── Tomcat Hosts: IC-PROD-03 (4 hosts — one Tomcat is stopped!) ───
INSERT INTO tomcat_hosts (service_id, hostname, app_version, db_name, db_service_id, health_status, status) VALUES
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-03' AND ms.service_name='Tomcat9_IC'),
 'tom.infocaption.com', '5.99', 'tomdb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-03' AND ms.service_type='mysql'),
 'error', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-03' AND ms.service_name='Tomcat9_IC'),
 'ulla.infocaption.com', '5.99', 'ulladb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-03' AND ms.service_type='mysql'),
 'error', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-03' AND ms.service_name='Tomcat9_IC'),
 'viktor.infocaption.com', '5.100', 'viktordb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-03' AND ms.service_type='mysql'),
 'error', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-03' AND ms.service_name='Tomcat9_IC'),
 'wendy.infocaption.com', '5.98', 'wendydb',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-PROD-03' AND ms.service_type='mysql'),
 'error', 'active');

-- ─── Tomcat Hosts: IC-TEST-01 (2 hosts) ───
INSERT INTO tomcat_hosts (service_id, hostname, app_version, db_name, db_service_id, health_status, status) VALUES
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-TEST-01' AND ms.service_name='Tomcat9_Test'),
 'test1.infocaption.com', '5.100', 'test1db',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-TEST-01' AND ms.service_type='mysql'),
 'ok', 'active'),
((SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-TEST-01' AND ms.service_name='Tomcat9_Test'),
 'test2.infocaption.com', '5.99', 'test2db',
 (SELECT ms.id FROM machine_services ms JOIN machines m ON ms.machine_id=m.id WHERE m.hostname='IC-TEST-01' AND ms.service_type='mysql'),
 'ok', 'active');
