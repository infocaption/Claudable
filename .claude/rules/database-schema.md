---
paths:
  - "icDashBoard/sql/**/*.sql"
  - "icDashBoard/src/main/java/**/DBUtil.java"
  - "icDashBoard/src/main/java/**/SmartassicDBUtil.java"
  - "install-package/install.sql"
---

# Database Schema (MySQL 5.7)

**Critical**: Use `utf8mb4_unicode_ci` collation for emoji support. Always use `--default-character-set=utf8mb4` when running SQL files via CLI.

## Tables

- **users**: id, username (unique), email (unique), password (BCrypt or 'SSO_NO_PASSWORD'), full_name, profile_picture_url, created_at, last_login, is_active, is_admin (TINYINT default 0)
- **modules**: id, owner_user_id (FK), module_type (enum: system/private/shared), name, icon, description, category, entry_file, directory_name (unique), badge, version, ai_spec_text, is_active, created_at, updated_at
- **customers** (companies): id, company_id (unique, SuperOffice ID), company_name, coach_email, track (VARCHAR 100, comma-separated: map,train,assist), is_active, first_seen, last_seen
- **servers** (URL instances): id, customer_id (FK), url, url_normalized (unique), machine_name, current_version, first_seen, last_seen, is_active
- **customer_stats_daily**: id, server_id (FK), snapshot_date, 14 metric columns, version — unique(server_id, snapshot_date). Metrics store daily (24h) data. Column names retain `_30d` suffixes for backward compat. API aggregates over configurable windows.
- **groups**: id, name (unique), icon (emoji default '&#128101;'), description, is_hidden, sso_department (VARCHAR 200), created_by (FK), created_at
- **user_groups**: user_id + group_id (composite PK, CASCADE both)
- **module_groups**: module_id + group_id (composite PK, CASCADE both)
- **email_templates**: id, owner_user_id (FK), name, subject, body_html, created_at, updated_at
- **email_sends**: id, sender_user_id (FK), template_id (FK), subject, body_html, recipient_count, sent_count, failed_count, status (enum), created_at, completed_at
- **email_recipients**: id, send_id (FK), email, status (enum), error_message, operation_id, sent_at
- **app_config**: config_key (PK VARCHAR 100), config_value (TEXT), category, description, is_secret, updated_at, updated_by (FK)
- **sync_configs**: id, name, source_url, auth_type (enum), auth_config (JSON TEXT), json_root_path, target_table, id_field_source, id_field_target, field_mappings (JSON TEXT), schedule_minutes, update_only, is_active, last_run_at/status/count, created_by, created_at, updated_at
- **sync_run_history**: id (BIGINT), config_id (FK CASCADE), started_at, completed_at, status (enum), records_fetched/upserted/failed, error_message, triggered_by (FK)
- **certificates**: id, server_host, port, common_name, issuer, valid_from, valid_to, days_left, serial_number, is_expired, keystore_path, cert_index, chain_json, last_scanned, created_at
- **certificate_chains**: id, certificate_id (FK), cert_index, subject, issuer, valid_from, valid_to, serial_number
- **license_keys**: id, server_id (FK), license_key_id (unique, SuperOffice x_licensekeyid), filename, server_url, server_version, license_holder, expiration_date (VARCHAR 50), created_at, updated_at. 1:N per server. Auto-created by CustomerStatsApiServlet.init(). In SyncExecutor ALLOWED_TABLES whitelist.
- **widgets**: id, name, icon, description, render_key (unique), custom_html, custom_js, refresh_seconds, is_active, created_by, created_at
- **widget_groups**: widget_id + group_id (composite PK, CASCADE both)
- **tomcat_instances**: id, name, machine_name, environment (enum), description, install_path (unique), is_active, last_scan_at, last_health_at, health_ok/warn/error (INT), http_port_override, https_port_override, is_ignored, created_at, updated_at
- **tomcat_connectors**: id, instance_id (FK CASCADE), port, is_ssl
- **tomcat_scan_hosts**: id, instance_id (FK CASCADE), hostname, app_base, is_ignored
- **tomcat_scan_host_aliases**: id, host_id (FK CASCADE to tomcat_scan_hosts), alias
- **tomcat_scan_host_contexts**: id, host_id (FK CASCADE to tomcat_scan_hosts), path, doc_base
- **tomcat_apps**: id, instance_id (FK CASCADE), name, context_path, has_web_inf, version
- **tomcat_users**: id, instance_id (FK CASCADE), username, roles
- **tomcat_health_results**: id, instance_id (FK CASCADE), target_name, target_host, target_url, context_path, doc_base, status (enum ok/warning/error), status_code, response_time_ms, error_message, checked_at

## Migrations (sql/)

| File | Purpose |
|------|---------|
| `001_customer_stats_tables.sql` | customers + customer_stats_daily |
| `002_user_profile_picture.sql` | profile_picture_url on users |
| `003_email_tables.sql` | email_templates, email_sends, email_recipients + Utskick module |
| `004_groups_tables.sql` | groups, user_groups, module_groups + 6 default groups |
| `005_email_templates_seed.sql` | 10 email templates (SV/EN/NO) |
| `006_servers_table.sql` | Separates customers from servers, migrates FK |
| `007_server_machine_name.sql` | machine_name + Serverlista module |
| `008_admin_and_sync.sql` | is_admin, sync_configs, sync_run_history |
| `009_app_config.sql` | app_config table + 20 defaults |
| `010_extended_metrics.sql` | login counts, unique producers |
| `011_certificates_table.sql` | certificates, certificate_chains + module |
| `012_widgets_tables.sql` | widgets, widget_groups + 7 defaults |
| `013_custom_widgets_and_auth.sql` | custom widget fields + auth.loginMethods |
| `013_sso_group_mapping.sql` | sso_department + 8 SSO config keys |
| `014_protect_alla_group.sql` | DELETE trigger protecting "Alla" |
| `022_superoffice_sync.sql` | 2 sync configs (Kunder + Serverkoppling) |
| `023_customer_tracks.sql` | track column on customers |
| `024_license_keys.sql` | license_keys table + Licenser sync config |
| `015_guide_planner.sql` | guide_projects, guide_tasks, guide_project_assignees |
| `015_user_preferences.sql` | user_preferences table |
| `016_jira_integration.sql` | jira_* tables |
| `017_drift_monitoring.sql` | drift_machines, drift_services, drift_hosts, server_health_state |
| `018_api_tokens.sql` | api_tokens table |
| `019_cloudguard.sql` | cloudguard_* tables |
| `020_incidents.sql` | incidents table |
| `021_backup_status.sql` | backup_status table |
| `025_mcp_gateway.sql` | mcp_servers table |
| `026_server_exclusion.sql` | server_exclusions table |
| `027_push_notifications.sql` | push_subscriptions table |
| `028_health_monitor.sql` | health_metrics table |
| `029_drift_ops_module.sql` | drift_ops module registration |
| `030_crypto_config.sql` | Encrypted config support |
| `031_audit_log.sql` | audit_logs table |
| `032_knowledge_base.sql` | kb_documents, kb_collections, kb_collection_documents |
| `033_customer_onboarding.sql` | is_onboarding flag on customers |
| `034_customer_renewal_date.sql` | renewal_date on customers |
| `035_customer_sales_fields.sql` | leadscore, engagement, upsell on customers |
| `036_tomcat_manager_configs.sql` | Tomcat Manager module registration + app_config keys |
| `037_tomcat_instances.sql` | tomcat_instances table |
| `038_tomcat_health_results.sql` | tomcat_health_results table |
| `039_tomcat_port_overrides.sql` | http/https port override columns |
| `040_tomcat_scan_data.sql` | tomcat_connectors, tomcat_scan_hosts, tomcat_scan_host_aliases, tomcat_scan_host_contexts, tomcat_apps, tomcat_users |
| `v1.0_baseline.sql` | Full baseline schema for fresh installs |

All applied (43 files total). Several tables also auto-created by servlet `init()` methods.

## External DB (smartassic)

- **Connection**: Configured in `app-secrets.properties` (keys: `db.smartassic.url`, `db.smartassic.user`, `db.smartassic.password`)
- **Tables**: `zsocompany`, `zsocontacts`, `zsoserver`
- **Charset**: Default latin1, but some columns use `utf8mb4_swedish_ci`
- **Connection class**: `SmartassicDBUtil.java` (uses AppConfig → SecretsConfig fallback chain)
- Used by: ContactListServlet (Listor), ServerListApiServlet (machine name sync)
