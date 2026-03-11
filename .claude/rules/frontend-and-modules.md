---
paths:
  - "icDashBoard/src/main/webapp/**/*.jsp"
  - "icDashBoard/src/main/webapp/**/*.js"
  - "icDashBoard/src/main/webapp/**/*.html"
  - "icDashBoard/src/main/webapp/**/*.css"
  - "icDashBoard/src/main/webapp/modules/**"
  - "icDashBoard/src/main/webapp/shared/**"
---

# Frontend, Modules & Widgets

## Module System

### Visibility Rules
Group filtering via `module_groups` table applies equally to all module types:
- **No group assignment** → visible to everyone
- **Assigned to "Alla"** → visible to everyone
- **Assigned to specific groups** → visible only to members of those groups
- **Private modules** → visible only to owner

### Architecture
- Modules are HTML files loaded in iframes from `/modules/{directory_name}/{entry_file}`
- Use `../../shared/ic-styles.css` and `../../shared/ic-utils.js`
- Call `ICUtils.notifyReady('module-id')` when loaded
- Upload via multipart form (max 50MB)
- Private→shared auto-assigns "Alla"; shared→private removes all group assignments

### Module Documentation Popup
- Floating **?** button (bottom-right) opens slide-in panel with description + AI spec text
- Modules trigger via `ICUtils.showDocPopup()` (sends `SHOW_MODULE_DOC` postMessage)
- `ModuleApiServlet` returns `aiSpecText` in JSON

### Built-in Modules (9)

| Module | Entry File | Description |
|--------|-----------|-------------|
| Kundstatistik | `customer-stats/displaystats.html` | Analytics dashboard with charts, Excel/CSV export |
| Utskick | `utskick/index.html` | Email broadcast with templates, Listor, history |
| Serverlista | `server-list/index.html` | Server overview with health checks |
| Trigga Handelser | `trigger-builder/index.html` | Client-side automation code generator |
| SQL Builder | `sql-builder/sql-builder.html` | SQL query builder UI |
| Verktygslada | `toolbox/toolbox.html` | PII scanner, JSON viewer, encoders, etc. |
| Dokumentation | `docs/docs.html` | Documentation viewer |
| Certifikat | `certificates/index.html` | SSL cert monitoring with expiry alerts |
| Pong | `pong/pong.html` | Demo game module |

### Customer Stats Module (Advanced)
- **CDN libs**: SheetJS, Chart.js 4.4.7, chartjs-adapter-date-fns 3.0.0
- **4 tabs**: Alla, Succe, Risk, Ovriga — each with table, card view, filters, version dropdown
- **URL params**: `?from=&to=&tab=` synced via `history.replaceState`
- **Dynamic column labels**: "Publ (30d)" updates to actual day count in range
- **Loading overlay**: Progressive status text during fetch + processing
- **Chunked rendering**: 50 rows / 40 cards per `requestAnimationFrame`
- **Debounced filters**: 200ms debounce on search inputs
- **Chart modal**: Per-customer history line chart, Day/Month toggle, 8 field toggles
- **Coach filter**: Popout with checkboxes, shows full name (LEFT JOIN users) or email fallback
- **Track filter**: `icon-flag` ic-icon, multi-select for Assist/Train/Map. Tracks in `customers.track` (comma-separated). Default: no filtering.
- **License column**: Color-coded expiry (green>60d, yellow<=60d, red<=30d, grey=none). Data from `license_keys` correlated subqueries.
- **Data model**: Stats per-server, not per-company. `customer_stats_daily.server_id` → `servers.id`
- **Export**: CSV per tab + Excel with all tabs. `globalData.alla` combined array.

### Utskick Module
- 4 tabs: Nytt utskick, Listor, Mallar, Historik
- **Listor**: Queries external smartassic DB. Filters: ServerUrl, MachineName, Domain, CompanyName, CompanyCategory, PersonCategory, Language. Language grouping with per-language transfer buttons.
- **Template variables**: `{{serverUrl}}`, `{{email}}`, `{{companyName}}`, `{{updateDate}}`
- **Azure ACS**: HMAC-SHA256 auth via `AcsEmailUtil.java`

### Serverlista Module
- Async health checks via Java 21 `HttpClient.sendAsync()` + `CompletableFuture`
- Background machine name sync from `smartassic.zsoserver` (interval: `server.syncInterval`)
- Severity: 200-299=ok, 404=medium, 401/403=low, 500+=severe

### Toolbox Module
- 10+ tools: Base64, Unicode, Cert decoder, Hash, JSON viewer, URL encoder, Color picker, Timestamp, Text diff, Regex, PII Scanner
- **PII Scanner**: Derived name detection, Swedish surname suffixes, signature detection, file upload, `_rensad` export

### SQL Builder Module
- Sidebar with category tree + custom queries (localStorage)
- **Visual Query Builder**: Flex sibling to sidebar (not overlay). Drag-drop from DB structure into SELECT/FROM/JOIN/WHERE/ORDER BY/LIMIT zones.

## Widget System
- `widgets` DB table, group-filtered via `widget_groups`
- User prefs in `localStorage` key `dashboard_widgets`
- Renderers: JS functions in `dashboard.jsp`'s `widgetRenderers` object
- Widget bar: top (default) or bottom position

### Built-in Widgets (7)
| Name | render_key | Refresh | Data Source |
|------|-----------|---------|-------------|
| Datum & Vecka | `date_week` | 60s | Client |
| Klocka | `clock` | 1s | Client |
| Serverstatus | `server_status` | 5min | `/api/servers/health` |
| Certifikat | `cert_expiry` | 10min | `/api/certificates` |
| Kundoversikt | `customer_count` | 10min | `/api/servers` |
| Snabblankar | `quick_links` | Never | localStorage |
| Teamoversikt | `team_online` | 10min | `/api/groups` |

New widgets need: DB row, renderer in `widgetRenderers`, optional `widget_groups` assignment.

## JSP Conventions
- `contentType="text/html; charset=UTF-8"` always
- Minimize scriptlets, logic in servlets
- `request.getContextPath()` for URLs
- JSTL available (`fn:escapeXml`)

## JavaScript Conventions
- Async IIFE pattern
- `fetch()` for API calls (auto-CSRF via global wrapper)
- `postMessage` for iframe ↔ parent communication

## postMessage API
Dashboard listens for these messages from iframe modules:
- `SHOW_TOAST` — show notification toast
- `SHOW_MODULE_DOC` — open module documentation popup
- `LOAD_MODULE` — load a module by ID in the iframe
- `LOAD_MANAGE_PAGE` — navigate to a management page
- `REFRESH_MODULES` — reload module list
- `GET_CSRF_TOKEN` → dashboard replies with `CSRF_TOKEN`

`ic-utils.js` provides `showToast()` and `showDocPopup()`.

## Manage Pages in Iframe
All management pages (create-module, manage-modules, manage-groups, manage-users, admin) load in dashboard iframe. Each JSP hides `.page-header` when `window !== window.top`. URL state: `?manage=pageId`.

## Prompt Generator
Both `create-module.jsp` and `manage-modules.jsp` have "Generera AI-prompt" button. Builds prompt from MODULE-SPEC.md + AI spec text. Includes `/icDashBoard/` context path and `../../api/...` relative paths.

## CSS & Icons
- Design system: `ic-styles.css` with CSS variables (`--ic-primary`, `--ic-bg-light`, etc.)
- BEM-like: `.ic-container`, `.ic-header`, `.ic-card`, `.ic-button`
- **ic-icons font** for all toolbar buttons (`icon-flag`, `icon-user-circle`, `icon-search`, `icon-refresh`, etc.)
- **No emojis for toolbar buttons** — use ic-icons
- No external CSS frameworks
