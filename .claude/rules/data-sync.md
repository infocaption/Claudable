---
paths:
  - "icDashBoard/src/main/java/**/Sync*.java"
  - "icDashBoard/src/main/java/**/SyncExecutor.java"
---

# Data Sync System

External data sync engine that fetches JSON from REST APIs and upserts into local DB tables.

## Components
- **`SyncExecutor.java`** — Core engine: HTTP fetch, JSON parse, dynamic SQL upsert
- **`SyncConfigServlet.java`** — CRUD API + test + manual run (load-on-startup=4)
- **`SyncSchedulerServlet.java`** — Background scheduler, checks every minute (load-on-startup=5)

## Features
- **Auth types**: none, api_key (custom header), bearer (Authorization: Bearer), basic (Base64)
- **JSON root path**: Dot-notation for nested JSON (e.g., `data.users`)
- **Field mapping**: JSON array `[{"source":"jsonField","target":"dbColumn"},...]`
- **FK lookup**: `{"source":"companyName","target":"customer_id","lookup":"customers.company_name"}` — resolves via subquery. Lookup tables validated against ALLOWED_TABLES whitelist.
- **Update-only mode**: `update_only=true` → only UPDATE, never INSERT
- **Upsert**: `INSERT ... ON DUPLICATE KEY UPDATE` based on id_field_source → id_field_target
- **Field transforms**: `fieldName|transform` pipe syntax. Currently: `url_normalize` (strips protocol+path, lowercases)
- **Table whitelist**: Only `customers`, `servers`, `license_keys` allowed as targets
- **Column validation**: Against `INFORMATION_SCHEMA.COLUMNS`
- **SQL injection protection**: Identifier validation (alphanumeric + underscore), backtick quoting, parameterized values
- **Overlap prevention**: `ConcurrentHashMap` tracks running configs (skips if already running)
- **History**: Logged to `sync_run_history` with counts, duration, errors, trigger source

## Pre-configured Sync Jobs

| Name | Target | Schedule | Mode | Key Feature |
|------|--------|----------|------|-------------|
| SuperOffice → Kunder | `customers` | 60min | Upsert | Maps companyId→company_id, companyName, coachEmail, track |
| SuperOffice → Serverkoppling | `servers` | 60min | Update-only | `serverUrl\|url_normalize` → `url_normalized`, FK lookup companyId→customer_id |
| SuperOffice → Licenser | `license_keys` | 60min | Upsert | Maps licensekeyid→license_key_id, FK lookup serverurl→server_id via url_normalize |

All use the same SuperOffice API base with different `includeId` parameters. Created via SQL migrations 022 + 024.

## Extensibility
- **New target tables**: Add table name to `SyncExecutor.getAllowedTables()`
- **New transforms**: Add case in `SyncExecutor.applyTransform()`
- **New sync config**: Create via Admin panel → Datasynk → "+ Ny synk" wizard
