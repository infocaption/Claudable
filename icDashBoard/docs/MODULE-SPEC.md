# InfoCaption Dashboard Module Specification (Java JSP)

> **Target audience:** AI models and automated code generation tools.
> For human-readable documentation, see `MODULE-GUIDE.md`.

---

## Overview

This specification defines the structure, conventions, and APIs for creating modules in the InfoCaption Dashboard Java JSP system. Modules are standalone HTML files loaded in an iframe within the main `dashboard.jsp`. Module metadata is stored in a MySQL database table.

### Architecture Context

The dashboard is a Java Servlet/JSP application running on Tomcat 9.0.100 with Java 21. Authentication is handled via servlet filters (password + SAML2 SSO). Modules are pure HTML/CSS/JS files served directly by Tomcat from `webapp/modules/{directory_name}/`. Module metadata (name, icon, description, category, visibility, AI spec) is stored in the `modules` database table. Access is controlled through a group-based permission system.

### Module Types & Visibility

| Type | Description | Default Visibility |
|------|-------------|-------------------|
| `system` | Built-in modules, no owner | Group-filtered (same as shared) |
| `private` | User-created, visible to owner only | Owner only |
| `shared` | User-created, shared with groups | Group-filtered |

**Group filtering** applies equally to `system` and `shared` modules via the `module_groups` table:
- **No group assignment** → visible to everyone
- **Assigned to "Alla" group** → visible to everyone (all users are implicit members)
- **Assigned to specific groups** → visible only to members of those groups

---

## Project Structure

```
icDashBoard/
├── src/main/java/com/infocaption/dashboard/
│   ├── filter/
│   │   ├── AuthFilter.java           # Guards dashboard, modules, API; API key auth for import
│   │   ├── CsrfFilter.java           # CSRF token validation (Synchronizer Token Pattern)
│   │   ├── EncodingFilter.java       # UTF-8 on all requests
│   │   └── SecurityHeaderFilter.java # Security headers (X-Content-Type-Options, X-Frame-Options, CSP)
│   ├── servlet/
│   │   ├── LoginServlet.java         # Password login + session (loads is_admin)
│   │   ├── RegisterServlet.java      # Registration (BCrypt)
│   │   ├── LogoutServlet.java        # Session invalidation
│   │   ├── SamlLoginServlet.java     # SP-initiated SAML → Entra ID redirect
│   │   ├── SamlAcsServlet.java       # SAML Assertion Consumer Service
│   │   ├── SamlMetadataServlet.java  # SP metadata XML
│   │   ├── ModuleApiServlet.java     # GET /api/modules → group-filtered JSON
│   │   ├── ModuleCreateServlet.java  # POST /module/create (multipart, 50MB)
│   │   ├── ModuleManageServlet.java  # POST /module/manage (edit/delete/groups)
│   │   ├── ModuleSpecServlet.java    # GET /api/module/spec → markdown export
│   │   ├── CustomerStatsApiServlet.java # Analytics: list, history, import
│   │   ├── EmailApiServlet.java      # 7 endpoints: template CRUD, send, history
│   │   ├── ContactListServlet.java   # External DB contact queries (Listor)
│   │   ├── ServerListApiServlet.java # Server list, health checks, machine name sync
│   │   ├── CertificateApiServlet.java # SSL certificate management
│   │   ├── AdminApiServlet.java      # Admin-only: user mgmt, group CRUD, widget CRUD, config (AppConfig)
│   │   ├── SyncConfigServlet.java    # Admin-only: data sync CRUD, test, run
│   │   ├── SyncSchedulerServlet.java # Background scheduler for auto sync
│   │   ├── GroupApiServlet.java      # Group list + join/leave
│   │   ├── GroupManageServlet.java   # Group CRUD + member management
│   │   ├── UserManageServlet.java    # User listing with Teams chat links
│   │   └── WidgetApiServlet.java     # GET /api/widgets → group-filtered JSON
│   ├── model/
│   │   ├── User.java                 # POJO: id, username, email, fullName, isAdmin
│   │   ├── Module.java               # POJO: matches modules DB columns
│   │   └── Group.java                # POJO: id, name, icon, isHidden, ssoDepartment, createdBy
│   └── util/
│       ├── AppConfig.java            # Centralized key-value config (cached, 5min TTL)
│       ├── DBUtil.java               # JDBC: local icdashboard DB (uses AppConfig)
│       ├── SmartassicDBUtil.java      # JDBC: external smartassic DB (uses AppConfig)
│       ├── AdminUtil.java            # Admin guard: requireAdmin() for admin-only APIs
│       ├── PasswordUtil.java         # BCrypt hash/verify (work factor 12)
│       ├── GroupUtil.java            # Session group loading, "Alla" group ID lookup
│       ├── JsonUtil.java             # JSON string quoting utility
│       ├── SamlConfigUtil.java       # Loads WEB-INF/saml.properties
│       ├── AcsEmailUtil.java         # Azure ACS HMAC-SHA256 email sender
│       ├── SyncExecutor.java         # Data sync engine: JSON → DB upsert
│       └── Esc.java                  # HTML entity escaping utility
├── src/main/webapp/
│   ├── WEB-INF/
│   │   ├── web.xml                   # 22 servlets, 4 filters, multipart config, HTTPS
│   │   ├── saml.properties           # SAML2 SSO config (Entra ID)
│   │   └── lib/                      # 12 JAR dependencies
│   ├── shared/
│   │   ├── ic-styles.css             # Design system with CSS variables
│   │   └── ic-utils.js              # Client-side utilities, postMessage API
│   ├── assets/logga.png              # InfoCaption logo
│   ├── modules/                      # 9 module directories
│   ├── login.jsp                     # Login (password + SSO)
│   ├── register.jsp                  # Registration
│   ├── dashboard.jsp                 # Main dashboard: sidebar, widget bar, iframe
│   ├── admin.jsp                     # Admin panel (5 tabs: Användare, Grupper, Datasynk, Widgets, Inställningar)
│   ├── create-module.jsp             # Module upload with group selection + prompt generator
│   ├── manage-modules.jsp            # Module admin + group assignment
│   ├── manage-groups.jsp             # Group CRUD + membership
│   └── manage-users.jsp              # User listing + Teams chat links
├── docs/
│   ├── MODULE-SPEC.md                # This file
│   ├── MODULE-GUIDE.md               # Human-readable guide (Swedish)
│   ├── SAML-SETUP.md                 # Azure Entra ID SAML2 setup
│   └── SECURITY-AUDIT.md            # Security audit report
└── sql/                              # 15 database migration scripts
```

---

## Module File Format

### HTML Template

Modules are pure HTML files (not JSP). No server-side directives needed.

```html
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{Module Name}</title>
    <link rel="stylesheet" href="../../shared/ic-styles.css">
    <style>
        /* Module-specific styles here */
    </style>
</head>
<body>
    <div class="ic-container">
        <div class="ic-header">
            <h1 class="ic-title">{icon} {Module Name}</h1>
            <p class="ic-subtitle">{Short description}</p>
        </div>

        <!-- Module content here -->

    </div>

    <script src="../../shared/ic-utils.js"></script>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            init();
            ICUtils.notifyReady('{module-id}');
        });

        function init() {
            // Module logic here
        }
    </script>
</body>
</html>
```

---

## Module Registration

Modules are registered in the `modules` MySQL table. The dashboard loads them via `GET /api/modules` which returns a group-filtered JSON array.

### Database Schema

```sql
CREATE TABLE modules (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id   INT             NULL,
    module_type     ENUM('system','private','shared') NOT NULL DEFAULT 'private',
    name            VARCHAR(100)    NOT NULL,
    icon            VARCHAR(20)     NOT NULL DEFAULT '📦',
    description     VARCHAR(500)    NULL,
    category        VARCHAR(50)     NOT NULL DEFAULT 'tools',
    entry_file      VARCHAR(255)    NOT NULL DEFAULT 'index.html',
    directory_name  VARCHAR(100)    NOT NULL UNIQUE,
    badge           VARCHAR(50)     NULL,
    version         VARCHAR(20)     NOT NULL DEFAULT '1.0',
    ai_spec_text    TEXT            NULL,
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### Group Assignment Tables

```sql
CREATE TABLE module_groups (
    module_id   INT NOT NULL,
    group_id    INT NOT NULL,
    PRIMARY KEY (module_id, group_id),
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id)  REFERENCES `groups`(id) ON DELETE CASCADE
);
```

### API Response Format

`GET /api/modules` returns (filtered by user's group memberships):

```json
[
    {
        "id": "customer-stats",
        "dbId": 1,
        "name": "Kundstatistik",
        "icon": "📈",
        "description": "Analysera kundstatistik...",
        "category": "analytics",
        "path": "/icDashBoard/modules/customer-stats/displaystats.html",
        "badge": null,
        "version": "1.0",
        "moduleType": "system",
        "isOwner": false,
        "hasAiSpec": false,
        "groups": ["Alla"]
    }
]
```

### Creating a Module via Upload

1. Navigate to `/module/create` (sidebar: "Skapa modul")
2. Fill in: name, icon (emoji), description, category, visibility
3. Upload: single `.html` file or `.zip` archive (max 50MB)
4. If shared: select target groups (defaults to "Alla")
5. Optionally write an AI specification text
6. Optionally generate an AI prompt (combines MODULE-SPEC + your spec)
7. Submit — creates directory under `webapp/modules/`, inserts DB record

### Creating a Module Manually

1. Create directory: `webapp/modules/{my-module}/`
2. Place `index.html` (or any `.html` entry file) in it
3. Insert into `modules` table:
```sql
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name)
VALUES (1, 'private', 'Min Modul', '🚀', 'Beskrivning', 'tools', 'index.html', 'my-module');
```

> **Important:** When using `mysql.exe` CLI on Windows, always connect with `--default-character-set=utf8mb4` for emoji support.

### Category Definitions

| ID | Purpose | Icon |
|----|---------|------|
| `analytics` | Data visualization, statistics, reports | Chart emoji |
| `tools` | Utilities, converters, editors | Tool emoji |
| `admin` | Configuration, settings, administration | Gear emoji |

---

## Authentication & Security

### Protected Resources
The `AuthFilter` intercepts requests to:
- `/dashboard.jsp`, `/admin.jsp`
- `/modules/*`
- `/api/*` (returns 401 JSON instead of redirect)
- `/module/*`, `/group/*`, `/manage-*`

### Public Resources
- `/login`, `/register`
- `/shared/*` (CSS, JS)
- `/assets/*` (images)
- `/saml/*` (SAML SSO endpoints)

### Session Model
- Tomcat `HttpSession` with `User` object as `session.getAttribute("user")`
- Session timeout: 30 minutes
- `User.isAdmin()` boolean flag for admin-only features
- `session.getAttribute("userGroupIds")` — `Set<Integer>` of group memberships

### Admin Role
- `users.is_admin` column (TINYINT, default 0)
- `AdminUtil.requireAdmin(req, resp)` guard for admin-only APIs
- All `/api/admin/*` and `/api/sync/*` endpoints require admin
- Admin panel (`admin.jsp`) redirects non-admins to dashboard

### Database
- MySQL 5.7 on `localhost:3306`, schema `icdashboard`
- Connection via `DBUtil.getConnection()` (uses AppConfig with hardcoded fallbacks)
- External DB: `SmartassicDBUtil` for smartassic DB (SuperOffice contacts)
- JDBC URL includes `connectionCollation=utf8mb4_unicode_ci` for emoji support

---

## Group System

### Core Concepts
- **"Alla" group**: All users are implicit members (no `user_groups` entry needed)
- **Visible groups** (`is_hidden=0`): Users can self-join/leave
- **Hidden groups** (`is_hidden=1`): Cannot be self-joined, members added manually
- **Module filtering**: User sees a module if they belong to ANY of its assigned groups

### Default Groups

| Name | Icon | Hidden | Description |
|------|------|--------|-------------|
| Alla | 🌐 | No | All users (implicit) |
| Kundvård | 💬 | No | Customer care team |
| Utveckling | 💻 | No | Development team |
| Support | 🎧 | No | Support team |
| Ledning | 👔 | Yes | Management |
| IT-säkerhet | 🔐 | Yes | IT security |

### API Endpoints

- `GET /api/groups` — visible groups + membership status
- `POST /api/groups` — `{"action":"join|leave","groupId":N}`
- `GET /group/manage` — group CRUD + member management UI

---

## Widget System

The dashboard includes a configurable widget bar that displays compact data widgets.

### Architecture
- Widgets defined in `widgets` DB table, group-filtered like modules
- User preferences stored in `localStorage` (enabled widgets + position)
- Widget renderers are JavaScript functions in `dashboard.jsp`
- Widget bar position: top (under topbar) or bottom (fixed)

### Built-in Widgets

| render_key | Name | Icon | Auto-refresh | Data Source |
|-----------|------|------|-------------|-------------|
| `date_week` | Datum & Vecka | 📅 | 1min | Client-side |
| `clock` | Klocka | 🕐 | 1s | Client-side |
| `server_status` | Serverstatus | 🖥️ | 5min | `/api/servers/health` |
| `cert_expiry` | Certifikat | 🔒 | 10min | `/api/certificates` |
| `customer_count` | Kundöversikt | 👥 | 10min | `/api/servers` |
| `quick_links` | Snabblänkar | 🔗 | Never | localStorage |
| `team_online` | Teamöversikt | 💬 | 10min | `/api/groups` |

### API
- `GET /api/widgets` — group-filtered widget list

---

## Application Configuration (AppConfig)

Centralized key-value config from `app_config` database table.

### Usage Pattern
```java
String value = AppConfig.get("key.name", FALLBACK_CONSTANT);
int timeout = AppConfig.getInt("http.connectTimeout", 30);
```

### Admin UI
Admin panel → "Inställningar" tab → edit any config value.

---

## CSS Design System Reference

### CSS Variables

```css
:root {
    --ic-primary: #5458F2;
    --ic-primary-hover: #4347d9;
    --ic-primary-light: #5e61f3;
    --ic-secondary: #D4D5FC;
    --ic-tertiary: #BEC0F9;
    --ic-accent: #FFE496;
    --ic-text-dark: #323232;
    --ic-text-light: #ffffff;
    --ic-text-muted: #6c757d;
    --ic-success: #10b981;
    --ic-success-light: #d1fae5;
    --ic-danger: #ef4444;
    --ic-danger-light: #fee2e2;
    --ic-warning: #f59e0b;
    --ic-warning-light: #fef3c7;
    --ic-bg-white: #ffffff;
    --ic-bg-light: #f8f9fc;
    --ic-radius-sm: 6px;
    --ic-radius-md: 10px;
    --ic-radius-lg: 16px;
    --ic-radius-xl: 24px;
    --ic-shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.06);
    --ic-shadow-md: 0 4px 12px rgba(0, 0, 0, 0.08);
    --ic-shadow-lg: 0 10px 30px rgba(0, 0, 0, 0.12);
    --ic-shadow-xl: 0 20px 50px rgba(0, 0, 0, 0.15);
}
```

### Component Classes

| Category | Classes |
|----------|---------|
| **Buttons** | `.ic-btn`, `.ic-btn-primary`, `.ic-btn-success`, `.ic-btn-danger`, `.ic-btn-ghost`, `.ic-btn-sm`, `.ic-btn-lg` |
| **Cards** | `.ic-card`, `.ic-card-header`, `.ic-card-title`, `.ic-card-body`, `.ic-card-footer` |
| **Forms** | `.ic-form-group`, `.ic-label`, `.ic-input`, `.ic-select`, `.ic-textarea`, `.ic-checkbox` |
| **Tables** | `.ic-table`, `.ic-table-striped`, `.ic-table-hover` |
| **Badges** | `.ic-badge`, `.ic-badge-primary`, `.ic-badge-success`, `.ic-badge-danger`, `.ic-badge-warning`, `.ic-badge-info`, `.ic-badge-accent` |
| **Alerts** | `.ic-alert`, `.ic-alert-info`, `.ic-alert-success`, `.ic-alert-warning`, `.ic-alert-danger` |
| **Layout** | `.ic-container`, `.ic-header`, `.ic-title`, `.ic-subtitle`, `.ic-section` |
| **Feedback** | `.ic-toast`, `.ic-spinner`, `.ic-modal`, `.ic-modal-header`, `.ic-modal-body`, `.ic-modal-footer` |
| **Data** | `.ic-summary-card`, `.ic-summary-value`, `.ic-summary-label`, `.ic-tabs`, `.ic-tab` |
| **Utilities** | `.ic-text-muted`, `.ic-text-success`, `.ic-text-danger`, `.ic-gradient-text`, `.ic-hidden` |

---

## ICUtils API Reference

### Dashboard Communication

```typescript
ICUtils.isInDashboard(): boolean
ICUtils.notifyReady(moduleId: string): void
ICUtils.showToast(message: string, type?: 'success' | 'error'): void
ICUtils.sendToDashboard(type: string, payload: any): void
```

### Formatting

```typescript
ICUtils.formatNumber(num: number): string       // "1 234 567"
ICUtils.formatPercent(num: number): string       // "+26%"
ICUtils.calculateChange(current: number, previous: number): number
ICUtils.formatDate(date: Date): string           // "2025-02-05"
ICUtils.formatDateTime(date: Date): string       // "2025-02-05 14:30"
```

### URL Handling

```typescript
ICUtils.normalizeUrl(url: string): string
ICUtils.getQueryParam(name: string): string | null
ICUtils.setQueryParam(name: string, value: string): void
```

### Storage (localStorage)

```typescript
ICUtils.saveToStorage(key: string, data: any): void
ICUtils.loadFromStorage(key: string, defaultValue?: any): any
ICUtils.removeFromStorage(key: string): void
```

### DOM Utilities

```typescript
ICUtils.createElement(tag: string, attrs: object, content: string): HTMLElement
ICUtils.debounce(func: Function, wait: number): Function
```

### Export

```typescript
ICUtils.exportToCSV(data: object[], filename: string, columns: {key: string, header: string}[]): void
ICUtils.copyToClipboard(text: string): Promise<void>
```

---

## Conventions

### Naming
- **Module ID / directory_name**: `kebab-case` (e.g., `sql-builder`, `customer-stats`)
- **Entry file**: `index.html` preferred, or descriptive name (e.g., `displaystats.html`)
- **CSS classes**: Prefix with `ic-` for shared, module-specific names for local
- **Storage keys**: Prefix with module ID (e.g., `sqlBuilder_settings`)

### Language
- UI text: **Swedish** (sv)
- Code comments: English or Swedish
- Variable names: English

### Code Style
- Use `const` and `let`, not `var`
- Use template literals for HTML generation
- Use `async`/`await` for promises
- Indent with 4 spaces

---

## Existing Modules Reference

| Module | Complexity | Demonstrates |
|--------|-----------|-------------|
| `pong` | Simple | Canvas, game loop, keyboard input |
| `toolbox` | Simple | Tabs, forms, clipboard, crypto |
| `sql-builder` | Medium | JSON data loading, dynamic rendering, variable substitution |
| `trigger-builder` | Medium | Wizard UI, code generation, template literals, clipboard |
| `docs` | Medium | Sidebar navigation, search, two-column layout |
| `server-list` | Medium | Backend API, async health checks, severity badges |
| `certificates` | Medium | Backend API, expiry alerts, color-coded status |
| `customer-stats` | Advanced | Tables, filtering (coach/track/version), sorting, license tracking, Excel/CSV export, Chart.js, toolbar with ic-icons |
| `utskick` | Advanced | Backend servlet, Azure ACS email, templates, Listor, history |

---

## Testing Checklist for New Modules

1. [ ] Valid HTML5 document structure
2. [ ] Includes `../../shared/ic-styles.css` in `<head>`
3. [ ] Includes `../../shared/ic-utils.js` before module script
4. [ ] Calls `ICUtils.notifyReady('{module-id}')` on load
5. [ ] Registered in `modules` database table
6. [ ] Responsive layout (test 320px - 1920px width)
7. [ ] No console errors
8. [ ] Works when loaded in dashboard iframe (authenticated)
9. [ ] Swedish language in UI
10. [ ] Uses CSS variables for colors (not hardcoded hex)

---

## AI-Assisted Module Creation Workflow

The primary way to create modules is through the built-in AI prompt generator.

### End-to-End Workflow
1. User fills in module name, icon, category, description in the **"Skapa modul"** form
2. User writes a free-form description of what the module should do in the **"AI-specifikation"** textarea
3. User clicks **"Kopiera AI-prompt"** — a complete prompt is copied to clipboard
4. User pastes the prompt into an AI tool (Claude, ChatGPT, etc.)
5. The AI generates a complete HTML file
6. User saves the HTML file and uploads it via the file upload area in the same form
7. User selects visibility (private or shared with groups) and clicks "Skapa modul"

### AI Specification Text
Each module has an `ai_spec_text` field stored in the database. This text:
- Is written when creating or editing a module
- Is combined with technical context by the prompt generator
- Can be exported as Markdown via `GET /api/module/spec?id=X&download=true`
- Is shown in the module's documentation popup (the floating **?** button)

### Prompt Generator
The **"Kopiera AI-prompt"** button (on create-module and manage-modules pages) generates a complete prompt that includes:
1. The user's module name, description, and category
2. The user's free-form AI specification text
3. Technical requirements (HTML format, CSS includes, ICUtils)
4. The full CSS design system (variables, component classes)
5. The ICUtils JavaScript API reference
6. All available API endpoints with request/response examples
7. Code conventions (Swedish UI, const/let, async/await, etc.)

The user does **not** need to know any technical details — the prompt generator provides everything the AI needs.

### Writing Good AI Specifications
Users should describe what they want in plain language:
- What the module should show (tables, charts, cards, etc.)
- What data it needs (which API endpoints)
- Desired interactions (sorting, filtering, search, export)
- Layout preferences (sidebar, tabs, toolbar)
- Special requirements (color coding, badges, real-time updates)

---

## Servlet/Filter Reference for Module Developers

If you need server-side logic for a module:

### Adding a New Servlet

1. Create Java class in `com.infocaption.dashboard.servlet`
2. Add `<servlet>` and `<servlet-mapping>` in `web.xml`
3. Access user: `User user = (User) request.getSession().getAttribute("user")`
4. Use `DBUtil.getConnection()` for database access
5. For admin-only: use `AdminUtil.requireAdmin(req, resp)` guard
6. Use `AppConfig.get("key", FALLBACK)` for configurable values
7. Return JSON with `response.setContentType("application/json")`

### Database Access Pattern

```java
try (Connection conn = DBUtil.getConnection();
     PreparedStatement ps = conn.prepareStatement("SELECT ...")) {
    ps.setString(1, param);
    ResultSet rs = ps.executeQuery();
    while (rs.next()) {
        // Process rows
    }
} catch (SQLException e) {
    e.printStackTrace();
}
```

### JSON Construction Pattern (no JSON library)

```java
StringBuilder sb = new StringBuilder("[");
while (rs.next()) {
    if (sb.length() > 1) sb.append(",");
    sb.append("{");
    sb.append("\"name\":").append(JsonUtil.quote(rs.getString("name")));
    sb.append(",\"count\":").append(rs.getInt("count"));
    sb.append("}");
}
sb.append("]");
```

---

## API Reference

### Customer Stats

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/customer-stats` | GET | Required | Aggregated stats (30d windows) + license data (count, nearest expiry, holder) |
| `/api/customer-stats/history` | GET | Required | Daily snapshots per server (`?url=X`) |
| `/api/customer-stats/import` | POST | API Key | Bulk import JSON array |

### Data Sync

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/sync/configs` | GET | Admin | List all sync configs |
| `/api/sync/configs` | POST | Admin | Create new sync config |
| `/api/sync/test` | POST | Admin | Test URL + auth, return JSON fields |
| `/api/sync/run?configId=N` | POST | Admin | Manually trigger a sync |
| `/api/sync/tables` | GET | Admin | List allowed target tables |
| `/api/sync/table-info?table=X` | GET | Admin | Get column metadata for a table |

### Email

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/email/templates` | GET | Required | List user's templates |
| `/api/email/templates` | POST | Required | Create template |
| `/api/email/templates/{id}` | PUT | Required | Update template |
| `/api/email/templates/{id}` | DELETE | Required | Delete template |
| `/api/email/send` | POST | Required | Send broadcast via Azure ACS |
| `/api/email/history` | GET | Required | Send history list |
| `/api/email/history/{id}` | GET | Required | Send detail + recipients |

### Contacts (External DB)

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/contacts/filters` | GET | Required | Filter options from smartassic DB |
| `/api/contacts/query` | POST | Required | Query contacts with filters |

### Servers

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/servers` | GET | Required | Server list + company + machine name |
| `/api/servers/health` | GET | Required | Async health checks on all servers |

### Certificates

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/certificates` | GET | Required | Certificate list (all or per server) |

### Groups

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/groups` | GET | Required | Visible groups + membership |
| `/api/groups` | POST | Required | Join/leave group |

### Widgets

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/widgets` | GET | Required | Group-filtered widget list |

### Admin (admin-only)

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/admin/users` | GET/POST | Admin | User list / toggle admin |
| `/api/admin/users/delete` | POST | Admin | Delete a user |
| `/api/admin/groups` | GET/POST/DELETE | Admin | Group CRUD (list/create/update/delete) |
| `/api/admin/groups/members` | POST | Admin | Add/remove group members |
| `/api/admin/widgets` | GET/POST/DELETE | Admin | Widget CRUD (list/create/update/delete) |
| `/api/admin/config` | GET/POST | Admin | AppConfig list / update |
| `/api/sync/configs` | GET/POST/PUT/DELETE | Admin | Sync config CRUD |
| `/api/sync/test` | POST | Admin | Test URL + auth |
| `/api/sync/run` | POST | Admin | Execute sync manually |
| `/api/sync/tables` | GET | Admin | Allowed target tables |
| `/api/sync/table-info` | GET | Admin | Column metadata |
| `/api/sync/history` | GET | Admin | Sync run history |

---

## Database Schema Summary

### Core Tables
- `users` — id, username, email, password, full_name, is_admin, is_active
- `modules` — id, owner_user_id, module_type, name, icon, description, category, entry_file, directory_name, ai_spec_text
- `groups` — id, name, icon, description, is_hidden, sso_department, created_by
- `user_groups` — user_id, group_id (many-to-many)
- `module_groups` — module_id, group_id (many-to-many)

### Analytics
- `customers` — id, company_id, company_name, is_active
- `servers` — id, customer_id, url, machine_name, current_version
- `customer_stats_daily` — server_id, snapshot_date, 14 metric columns

### Email
- `email_templates` — id, owner_user_id, name, subject, body_html
- `email_sends` — id, sender_user_id, subject, recipient_count, status
- `email_recipients` — id, send_id, email, status, error_message

### Admin
- `app_config` — config_key, config_value, category, is_secret
- `sync_configs` — id, name, source_url, auth_type, target_table, field_mappings
- `sync_run_history` — id, config_id, status, records_upserted

### Certificates
- `certificates` — id, server_host, common_name, issuer, valid_from, valid_to, days_left
- `certificate_chains` — id, certificate_id, cert_index, subject, issuer

### Widgets
- `widgets` — id, name, icon, description, render_key (UNIQUE), custom_html, custom_js, refresh_seconds, created_by
- `widget_groups` — widget_id, group_id (many-to-many)
