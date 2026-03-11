-- 012_widgets_tables.sql
-- Creates widgets and widget_groups tables for mini-widget system
-- Widgets are compact visual components shown in a configurable bar on the dashboard

CREATE TABLE IF NOT EXISTS widgets (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    icon        VARCHAR(10)   NOT NULL DEFAULT '📦' COMMENT 'Emoji icon',
    description VARCHAR(500)  NULL,
    render_key  VARCHAR(50)   NOT NULL UNIQUE COMMENT 'Maps to JS renderer function',
    is_active   TINYINT       NOT NULL DEFAULT 1,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS widget_groups (
    widget_id   INT NOT NULL,
    group_id    INT NOT NULL,
    PRIMARY KEY (widget_id, group_id),
    CONSTRAINT fk_wg_widget FOREIGN KEY (widget_id) REFERENCES widgets(id) ON DELETE CASCADE,
    CONSTRAINT fk_wg_group  FOREIGN KEY (group_id)  REFERENCES `groups`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed 7 built-in widgets
INSERT INTO widgets (name, icon, description, render_key) VALUES
    ('Datum & Vecka', '📅', 'Visar dagens datum och veckonummer', 'date_week'),
    ('Klocka',        '🕐', 'Digital klocka med sekunder',        'clock'),
    ('Serverstatus',  '🖥️', 'Sammanfattning av servrarnas hälsa', 'server_status'),
    ('Certifikat',    '🔒', 'Antal utgångna och kritiska certifikat', 'cert_expiry'),
    ('Kundöversikt',  '📊', 'Antal aktiva servrar och kunder',    'customer_count'),
    ('Snabblänkar',   '🔗', 'Personliga bokmärken och genvägar',  'quick_links'),
    ('Teamöversikt',  '👥', 'Dina grupper och antal medlemmar',   'team_online')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Assign all widgets to "Alla" group (visible to everyone)
INSERT IGNORE INTO widget_groups (widget_id, group_id)
SELECT w.id, g.id FROM widgets w, `groups` g
WHERE g.name = 'Alla';
