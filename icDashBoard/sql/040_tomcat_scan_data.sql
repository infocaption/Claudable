-- Migration 040: Persist Tomcat scan data (connectors, hosts, apps, users)
-- These tables store the results of scanning server.xml + webapps/ so that
-- data is available immediately without re-scanning the filesystem.
-- NOTE: tomcat_hosts already exists (from 037) with different schema,
-- so scan-related host tables use the "tomcat_scan_" prefix.

CREATE TABLE IF NOT EXISTS tomcat_connectors (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    instance_id  INT NOT NULL,
    port         INT NOT NULL,
    is_ssl       TINYINT(1) DEFAULT 0,
    FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE,
    INDEX idx_tc_inst (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tomcat_scan_hosts (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    instance_id  INT NOT NULL,
    hostname     VARCHAR(255) NOT NULL,
    app_base     VARCHAR(500) NULL,
    is_ignored   TINYINT(1) DEFAULT 0,
    FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE,
    INDEX idx_tsh_inst (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tomcat_scan_host_aliases (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    host_id   INT NOT NULL,
    alias     VARCHAR(255) NOT NULL,
    FOREIGN KEY (host_id) REFERENCES tomcat_scan_hosts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tomcat_scan_host_contexts (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    host_id   INT NOT NULL,
    path      VARCHAR(255) DEFAULT '/',
    doc_base  VARCHAR(500) NULL,
    FOREIGN KEY (host_id) REFERENCES tomcat_scan_hosts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tomcat_apps (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    instance_id   INT NOT NULL,
    name          VARCHAR(255) NOT NULL,
    context_path  VARCHAR(255) NOT NULL,
    has_web_inf   TINYINT(1) DEFAULT 0,
    version       VARCHAR(100) NULL,
    FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE,
    INDEX idx_ta_inst (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tomcat_users (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    instance_id  INT NOT NULL,
    username     VARCHAR(255) NOT NULL,
    roles        VARCHAR(500) NULL,
    FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE,
    INDEX idx_tu_inst (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
