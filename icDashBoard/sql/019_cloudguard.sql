-- Migration 019: CloudGuard incident reporting system
-- Dedicated incident table with report/resolve lifecycle.
-- Replaces the old aggregation-based /api/drift/cloudguard endpoint.

CREATE TABLE IF NOT EXISTS cloudguard (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    entity_name     VARCHAR(500) NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL DEFAULT 'service',
    severity        VARCHAR(20)  NOT NULL DEFAULT 'error',
    message         TEXT         NULL,
    reported_by     VARCHAR(255) NULL,
    reported_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP    NULL,
    resolved_by     VARCHAR(255) NULL,
    resolution_note TEXT         NULL,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    INDEX idx_cg_active   (is_active, severity),
    INDEX idx_cg_entity   (entity_name(100)),
    INDEX idx_cg_reported (reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
