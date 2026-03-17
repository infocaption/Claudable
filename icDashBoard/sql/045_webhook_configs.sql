-- 045: Webhook configs for inbound data endpoints
-- External systems can POST JSON data to webhook URLs for automatic upsert into DB tables

CREATE TABLE IF NOT EXISTS webhook_configs (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    url_token       VARCHAR(64) NOT NULL UNIQUE,
    auth_type       ENUM('none','api_key','bearer') NOT NULL DEFAULT 'none',
    auth_config     TEXT NULL,
    target_table    VARCHAR(100) NOT NULL,
    id_field_source VARCHAR(255) NOT NULL,
    id_field_target VARCHAR(255) NOT NULL,
    field_mappings  TEXT NOT NULL,
    update_only     TINYINT(1) NOT NULL DEFAULT 0,
    is_active       TINYINT(1) NOT NULL DEFAULT 1,
    last_received_at TIMESTAMP NULL,
    last_received_count INT NULL DEFAULT 0,
    created_by      INT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS webhook_run_history (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    webhook_id        INT NOT NULL,
    received_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status            ENUM('success','failed') DEFAULT 'success',
    records_received  INT DEFAULT 0,
    records_upserted  INT DEFAULT 0,
    records_failed    INT DEFAULT 0,
    error_message     TEXT NULL,
    source_ip         VARCHAR(45) NULL,
    CONSTRAINT fk_wh_config FOREIGN KEY (webhook_id) REFERENCES webhook_configs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
