-- ============================================================
-- icDashBoard v1.0.0 Baseline
-- Creates the schema_version table and marks v1.0.0 as applied.
-- All existing migrations (001–021) are part of this baseline.
-- ============================================================

CREATE TABLE IF NOT EXISTS schema_version (
    version       VARCHAR(20)  NOT NULL PRIMARY KEY,
    description   VARCHAR(255) NOT NULL,
    applied_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    applied_by    VARCHAR(100) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO schema_version (version, description)
VALUES ('1.0.0', 'Baseline - alla befintliga tabeller och moduler');
