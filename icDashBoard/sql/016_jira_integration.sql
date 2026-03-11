-- 016_jira_integration.sql
-- Registers Jira module, widget, and app config for timeout

-- Add Jira connect timeout to app_config
INSERT INTO app_config (config_key, config_value, category, description, is_secret, updated_by)
VALUES ('jira.connectTimeout', '15', 'general', 'Timeout för Jira API-anrop i sekunder', 0, NULL)
ON DUPLICATE KEY UPDATE config_key = config_key;

-- Register Jira module
INSERT INTO modules (
    owner_user_id, module_type, name, icon, description, category,
    entry_file, directory_name, badge, version, ai_spec_text, is_active
) VALUES (
    NULL, 'system', 'Jira', '📋',
    'Visar dina aktiva Jira-ärenden med direktlänkar till Jira',
    'Integration', 'index.html', 'jira', NULL, '1.0.0',
    'Jira-integration som visar aktiva ärenden för den inloggade användaren. Kräver att användaren konfigurerat Jira-inställningar (domän, e-post, API-token) i Inställningar. Hämtar ärenden från Jira REST API v3.',
    1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Assign Jira module to "Alla" group
INSERT IGNORE INTO module_groups (module_id, group_id)
SELECT m.id, g.id
FROM modules m, `groups` g
WHERE m.directory_name = 'jira' AND g.name = 'Alla';

-- Register Jira widget
INSERT INTO widgets (name, icon, description, render_key, refresh_seconds, is_active)
VALUES (
    'Jira-ärenden', '📋',
    'Antal öppna Jira-ärenden tilldelade dig',
    'jira_issues', 300, 1
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Assign Jira widget to "Alla" group
INSERT IGNORE INTO widget_groups (widget_id, group_id)
SELECT w.id, g.id
FROM widgets w, `groups` g
WHERE w.render_key = 'jira_issues' AND g.name = 'Alla';
