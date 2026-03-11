-- 015_guide_planner.sql
-- Guide Planner module: project planning for guide updates/translations
-- Shared across all dashboard users (not per-user)

-- ─── Projects ───
CREATE TABLE IF NOT EXISTS guide_projects (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_by  INT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_gp_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Tasks ───
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

-- ─── Project Assignees ───
CREATE TABLE IF NOT EXISTS guide_project_assignees (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    project_id      INT NOT NULL,
    assignee_name   VARCHAR(255) NOT NULL,
    CONSTRAINT fk_gpa_project FOREIGN KEY (project_id) REFERENCES guide_projects(id) ON DELETE CASCADE,
    UNIQUE KEY uq_gpa_project_assignee (project_id, assignee_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Register module ───
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name, is_active)
VALUES (NULL, 'system', 'Guide Planner', '📋', 'Projektplanering för guideuppdateringar och översättningar', 'tools', 'index.html', 'guide-planner', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Assign to "Alla" group (visible to everyone)
INSERT IGNORE INTO module_groups (module_id, group_id)
SELECT m.id, g.id FROM modules m, `groups` g
WHERE m.directory_name = 'guide-planner' AND g.name = 'Alla';

-- Seed default project
INSERT INTO guide_projects (name) VALUES ('Projekt 1');
