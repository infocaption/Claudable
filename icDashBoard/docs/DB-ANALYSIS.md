# Databasanalys — icdashboard

Datum: 2026-03-16
MySQL 5.7, alla tabeller InnoDB + utf8mb4_unicode_ci.

## Sammanfattning

- **49 tabeller**, ~2.5 MB total storlek
- **47 foreign keys** — referensintegriteten ser bra ut, inga orphans
- Alla tabeller och kolumner använder utf8mb4_unicode_ci (inga charset-problem)
- `schema_version`-tabell finns men med luckor i versionering

---

## Relationsstruktur

```
users (hub) ──┬── api_tokens
              ├── user_groups ── groups ──┬── module_groups ── modules
              ├── user_preferences       └── widget_groups ── widgets
              ├── email_sends ── email_recipients
              ├── email_templates
              ├── guide_projects ── guide_tasks / guide_project_assignees
              ├── incidents
              ├── push_subscriptions / notification_log
              └── (created_by FK i 10+ tabeller)

customers ──── servers ──┬── customer_stats_daily
                         ├── license_keys
                         ├── server_health_state
                         └── tomcat_hosts

machines ── machine_services ──┬── tomcat_manager_configs
                               └── tomcat_hosts

tomcat_instances ──┬── tomcat_apps
                   ├── tomcat_connectors
                   ├── tomcat_users
                   ├── tomcat_health_results
                   └── tomcat_scan_hosts ── aliases / contexts

mcp_servers ── mcp_audit_log
kb_collections ── kb_collection_documents ── kb_documents
sync_configs ── sync_run_history
cloudguard (standalone)
backup_status (standalone)
certificates (standalone)
```

## Tabellstorlekar (topp 10)

| Tabell | Rader | Storlek |
|--------|------:|--------:|
| cloudguard | 845 | 432 KB |
| customer_stats_daily | 1584 | 272 KB |
| incidents | 477 | 176 KB |
| customers | 686 | 144 KB |
| servers | 278 | 128 KB |
| license_keys | 734 | 128 KB |
| tomcat_hosts | 21 | 96 KB |
| sync_run_history | 459 | 96 KB |
| users | 2 | 80 KB |
| email_templates | 10 | 64 KB |

---

## Åtgärdslista

### Prioritet 1 — Bör fixas

#### Redundanta index på `users`

`email` har både UNIQUE-constraint och separat `idx_email`. Samma för `username`. Redundanta index kostar skrivprestanda utan nytta.

```sql
-- Migration: drop_redundant_user_indexes
DROP INDEX idx_email ON users;
DROP INDEX idx_username ON users;
```

#### Droppa `customers_old`

Gammalt schema (url-baserat) innan normalisering till customers + servers. Alla 8 rader har migrerats. Ingen kod refererar till tabellen längre.

```sql
-- Migration: drop_customers_old
DROP TABLE IF EXISTS customers_old;
```

### Prioritet 2 — Prestandaförbättringar

#### Saknade index för vanliga queries

Baserat på servlet-kod och JOIN-mönster:

| Tabell | Kolumn(er) | Anledning | SQL |
|--------|-----------|-----------|-----|
| `sync_run_history` | `created_at` | ORDER BY i historik-vy | `CREATE INDEX idx_synchistory_created ON sync_run_history (created_at);` |
| `email_sends` | `created_at` | ORDER BY i historik-vy | `CREATE INDEX idx_emailsends_created ON email_sends (created_at);` |
| `cloudguard` | `created_at` | ORDER BY + pagination | `CREATE INDEX idx_cg_created ON cloudguard (created_at);` |
| `incidents` | `status` | WHERE-filter i listvy | `CREATE INDEX idx_inc_status ON incidents (status);` |

Dessa är låg-risk att lägga till (INSERT-kostnaden är försumbar vid dessa volymer).

### Prioritet 3 — Städning / övrigt

#### 3 servrar utan customer-koppling

```
id=43  infokoping.infocaption.com
id=66  pe.infocaption.com
id=145 predguide.esv.se
```

Dessa syns i kundstatistik utan företagsnamn. Bör antingen:
- Kopplas till rätt customer via `UPDATE servers SET customer_id = ? WHERE id = ?`
- Eller exkluderas om de är test/demo

#### `schema_version` har luckor

Nuvarande versioner: 1.0.0 → 1.7.0 → 1.8.0 → 1.29.0. Hoppet från 1.8 till 1.29 tyder på att version-tracking inte uppdateras konsekvent vid varje migration. Överväg att antingen:
- Uppdatera schema_version vid varje migration
- Eller byta till enklare migrationsnummer-tracking

#### Duplicerade SQL-migrationsnummer (fixat)

013 och 015 hade duplicerade nummer. Fixat genom att byta till 013b/015b. Framöver: kontrollera `ls sql/` innan ny migration skapas.

---

## Kodrelaterade DB-problem (fixade 2026-03-16)

Dessa har redan åtgärdats men dokumenteras för referens:

| Problem | Fix | Commit |
|---------|-----|--------|
| ~50 ResultSet-läckor (ej try-with-resources) | Wrappat alla i try-with-resources | `f67762a` |
| `customers` CREATE TABLE saknade `is_excluded` | Lagt till i init() | `e4a652f` (amendad) |
| JsonUtil.extractJsonString hanterade ej escaped quotes | Ny regex + unescapeJson() | `af5b3a2` |

---

## Hardcoded credentials (PowerShell)

Flera PS-skript i `Ny mapp/` har hardcoded DB-lösenord i connection strings. Finns TODO-kommentarer men aldrig åtgärdat. Berörda filer:

- `getstats.ps1`
- `allstats.ps1`
- `backfill-stats.ps1`
- `getcerts.ps1`
- `check-backups.ps1`
- `sync-companies.ps1`
- `getdbbroker.ps1`
- `azure_send_mail.ps1` (Azure ACS connection string)

**Rekommendation**: Flytta till environment variables eller en delad config-fil som inte versionshanteras.
