-- Migration 007: Add machine_name to servers + register Server List module
-- Run: "C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < 007_server_machine_name.sql

-- Step 1: Add machine_name column to servers table
ALTER TABLE servers ADD COLUMN machine_name VARCHAR(255) NULL AFTER url_normalized;

-- Step 2: Register the Server List module
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name)
VALUES (NULL, 'system', 'Serverlista', '🖥️', 'Översikt av alla servrar med hälsostatus, version och maskinnamn', 'tools', 'index.html', 'server-list');

-- Step 3: Assign module exclusively to "Drift" group
INSERT INTO module_groups (module_id, group_id)
SELECT m.id, g.id FROM modules m, `groups` g
WHERE m.directory_name = 'server-list' AND g.name = 'Drift';
