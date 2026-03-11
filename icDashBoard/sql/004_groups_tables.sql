-- Migration 004: Group-based access control for modules
-- Adds groups, user_groups, and module_groups tables
-- Groups control which users can see which shared modules
-- "Alla" is a special default group that all users implicitly belong to
-- Hidden groups (is_hidden=1) require manual member assignment

-- ============================================================
-- 1. Create tables
-- ============================================================

CREATE TABLE IF NOT EXISTS `groups` (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    icon        VARCHAR(20)  NOT NULL DEFAULT '👥',
    description VARCHAR(500) NULL,
    is_hidden   TINYINT(1)   NOT NULL DEFAULT 0,
    created_by  INT          NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
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
-- 2. Seed default groups
-- ============================================================

INSERT IGNORE INTO `groups` (name, icon, description, is_hidden) VALUES
('Alla',         '🌐', 'Alla användare — standardgrupp som alla tillhör', 0),
('Kundvård',     '💬', 'Kundvårdsteamet', 0),
('Utveckling',   '💻', 'Utvecklingsteamet', 0),
('Support',      '🎧', 'Supportteamet', 0),
('Ledning',      '👔', 'Ledningsgruppen', 1),
('IT-säkerhet',  '🔐', 'IT-säkerhetsteamet', 1);

-- ============================================================
-- 3. Backward compatibility: migrate existing modules to groups
-- ============================================================

-- All system modules → assign to "Alla" group
INSERT INTO module_groups (module_id, group_id)
SELECT m.id, g.id FROM modules m, `groups` g
WHERE m.module_type = 'system' AND g.name = 'Alla'
ON DUPLICATE KEY UPDATE module_id = module_id;

-- All shared modules → assign to "Alla" group
INSERT INTO module_groups (module_id, group_id)
SELECT m.id, g.id FROM modules m, `groups` g
WHERE m.module_type = 'shared' AND g.name = 'Alla'
ON DUPLICATE KEY UPDATE module_id = module_id;
