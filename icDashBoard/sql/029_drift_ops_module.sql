-- 029_drift_ops_module.sql — Combine 6 drift modules into one tabbed module
-- Inserts new "Drift & Övervakning" module, deactivates the 6 individual ones,
-- and migrates group mappings.

SET NAMES utf8mb4;

-- 1. Insert the new combined module
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name)
VALUES (NULL, 'system', 'Drift & Övervakning', '🖥️', 'Samlad driftvy — infrastruktur, servrar, certifikat, backup, CloudGuard och incidenter', 'admin', 'index.html', 'drift-ops')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), is_active = 1;

-- 2. Deactivate the 6 individual modules (keep data, just hide from nav)
UPDATE modules SET is_active = 0
WHERE directory_name IN ('drift-monitor', 'server-list', 'certificates', 'backup-status', 'cloudguard-monitor', 'incidents');

-- 3. Migrate group mappings: give drift-ops the same group access as the old modules
INSERT IGNORE INTO module_groups (module_id, group_id)
SELECT
    (SELECT id FROM modules WHERE directory_name = 'drift-ops'),
    mg.group_id
FROM module_groups mg
JOIN modules m ON m.id = mg.module_id
WHERE m.directory_name IN ('drift-monitor', 'server-list', 'certificates', 'backup-status', 'cloudguard-monitor', 'incidents');

-- 4. Track migration
INSERT IGNORE INTO schema_version (version, description)
VALUES ('1.29.0', 'Combine 6 drift modules into drift-ops tabbed module');
