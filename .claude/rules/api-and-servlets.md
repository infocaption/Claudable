---
paths:
  - "icDashBoard/src/main/java/**/*.java"
---

# API & Servlet Patterns

## Key URL Routes

| URL | Servlet | Auth | Purpose |
|-----|---------|------|---------|
| `/login` | LoginServlet | Public | Login form + auth |
| `/register` | RegisterServlet | Public | Registration |
| `/logout` | LogoutServlet | Any | Session invalidation |
| `/saml/login` | SamlLoginServlet | Public | SAML2 SSO redirect |
| `/saml/acs` | SamlAcsServlet | Public | SAML2 Assertion Consumer |
| `/saml/metadata` | SamlMetadataServlet | Public | SP metadata XML |
| `/dashboard.jsp` | (JSP + AuthFilter) | Required | Main dashboard |
| `/admin.jsp` | (JSP + AuthFilter) | Admin | Admin panel (5 tabs) |
| `/api/modules` | ModuleApiServlet | Required | Module list JSON (group-filtered) |
| `/api/module/spec` | ModuleSpecServlet | Required | AI spec export |
| `/api/customer-stats` | CustomerStatsApiServlet | API Key/Auth | Analytics (aggregated) |
| `/api/customer-stats/history` | CustomerStatsApiServlet | Required | Daily history per customer |
| `/api/customer-stats/import` | CustomerStatsApiServlet | API Key | Bulk import (JSON array) |
| `/api/email/templates` | EmailApiServlet | Required | Email template CRUD |
| `/api/email/send` | EmailApiServlet | Required | Send email via Azure ACS |
| `/api/email/history` | EmailApiServlet | Required | Send history |
| `/api/contacts/filters` | ContactListServlet | Required | Filter options from external DB |
| `/api/contacts/query` | ContactListServlet | Required | Query contacts with filters |
| `/api/servers` | ServerListApiServlet | Required | Server list |
| `/api/servers/health` | ServerListApiServlet | Required | Async health checks |
| `/api/admin/users` | AdminApiServlet | Admin | User admin + toggle admin |
| `/api/admin/users/delete` | AdminApiServlet | Admin | Delete user |
| `/api/admin/groups` | AdminApiServlet | Admin | Group CRUD |
| `/api/admin/groups/members` | AdminApiServlet | Admin | Group member management |
| `/api/admin/widgets` | AdminApiServlet | Admin | Widget CRUD |
| `/api/admin/config` | AdminApiServlet | Admin | App config management |
| `/api/sync/configs` | SyncConfigServlet | Admin | Sync config CRUD |
| `/api/sync/test` | SyncConfigServlet | Admin | Test URL + auth |
| `/api/sync/run` | SyncConfigServlet | Admin | Execute sync manually |
| `/api/sync/tables` | SyncConfigServlet | Admin | Allowed target tables |
| `/api/sync/table-info` | SyncConfigServlet | Admin | Column metadata |
| `/api/sync/history` | SyncConfigServlet | Admin | Sync run history |
| `/api/groups` | GroupApiServlet | Required | Group list + join/leave |
| `/api/certificates` | CertificateApiServlet | Required | Certificate list |
| `/api/widgets` | WidgetApiServlet | Required | Group-filtered widget list |
| `/group/manage` | GroupManageServlet | Required | Group CRUD + members |
| `/manage-users` | UserManageServlet | Required | User listing |
| `/module/create` | ModuleCreateServlet | Required | Upload modules (50MB max) |
| `/module/manage` | ModuleManageServlet | Required | Edit/delete modules |

## Servlet Development Patterns

### JSON Construction
No JSON library — all JSON constructed with `StringBuilder`:
```java
StringBuilder sb = new StringBuilder("[");
// ... build JSON manually
sb.append("]");
resp.getWriter().write(sb.toString());
```
Parse incoming JSON with regex helpers: `extractJsonString()`, `extractJsonInt()`, `extractJsonArray()`, `extractJsonObject()`.

### Configuration Pattern
Use `AppConfig.get("key", FALLBACK_CONSTANT)` for all configurable values:
```java
private static final String FALLBACK_URL = "jdbc:mysql://localhost:3306/icdashboard?...";
String url = AppConfig.get("db.url", FALLBACK_URL);
```
Keep `FALLBACK_` constant as backup — system must work without DB config.

### AppConfig Bootstrap
**Circular dependency**: AppConfig needs DBUtil to load config, but DBUtil needs AppConfig for connection params.
**Solution**: `volatile boolean loading` re-entrancy guard. During first startup, DBUtil always uses hardcoded fallback values. After AppConfig loads, overridden values from `app_config` table are used.

### Config Keys (grouped by category)

| Key | Category | Secret | Used By |
|-----|----------|--------|---------|
| `db.url`, `db.user`, `db.password` | database | url:No, others:Yes | DBUtil |
| `db.smartassic.url/user/password` | database | url:No, others:Yes | SmartassicDBUtil |
| `acs.endpoint/host/accessKey/senderAddress/apiVersion` | email | accessKey:Yes | AcsEmailUtil |
| `api.importKey` | security | Yes | AuthFilter |
| `email.sendTimeout/statusTimeout/historyLimit` | email | No | EmailApiServlet |
| `contacts.maxResults` | general | No | ContactListServlet |
| `http.connectTimeout`, `sync.connectTimeout` | general | No | SyncExecutor |
| `server.healthCheckTimeout/syncInterval` | general | No | ServerListApiServlet |
| `auth.loginMethods` | security | No | LoginServlet/login.jsp |
| `sso.*` (8 keys) | security | No | SamlAcsServlet |

### Adding New Config Keys
1. Add row to `app_config` table (or INSERT in a migration SQL)
2. Replace hardcoded value with `AppConfig.get("key.name", HARDCODED_FALLBACK)`
3. Keep original as `FALLBACK_` constant
4. Key auto-appears in Admin panel Instellningar tab

### Admin-Only Endpoints
Guard with `AdminUtil.requireAdmin(req, resp)` at top of handler:
```java
User admin = AdminUtil.requireAdmin(req, resp);
if (admin == null) return; // 401/403 already sent
```

### Servlet Init Patterns
- Servlets needing tables: `CREATE TABLE IF NOT EXISTS` in `init()` for idempotent startup
- Background tasks: use `load-on-startup` in web.xml

### Admin Panel (5 tabs)
1. **Anvandare**: User card grid, admin toggle, delete, search
2. **Grupper**: Group CRUD, member management, SSO badge, hidden badge
3. **Datasynk**: Sync config cards, step-by-step creation modal, run history
4. **Widgets**: Widget CRUD, custom HTML/JS, group assignment
5. **Installningar**: `app_config` editor grouped by category, secret masking
