# CLAUDE.md — InfoCaption Dashboard (icDashBoard)

## Project Overview

Java Servlet/JSP dashboard platform for InfoCaption support organizations. Database-driven module management, password + SAML2 SSO auth, group-based access control, admin system, centralized config, data sync, and multi-tier module visibility. Modules load as iframes.

## Tech Stack

- **Java 21** (Eclipse Adoptium JDK 21.0.10)
- **Apache Tomcat 9.0.100** (Servlet 4.0, included in repo)
- **MySQL 5.7** — database `icdashboard`, user `icdashboarduser`
- **BCrypt** (jbcrypt-0.4.jar, work factor 12) for password hashing
- **OneLogin java-saml 2.9.0** for SAML2 SSO against Microsoft Entra ID
- **MySQL Connector/J 8.4.0** for JDBC
- **Eclipse IDE** with WTP (Dynamic Web Project)
- **No build tool** (Maven/Gradle) — Eclipse auto-compiles to `build/classes/`

## Project Structure

```
claudable/
├── icDashBoard/
│   ├── src/main/java/com/infocaption/dashboard/
│   │   ├── filter/          # 7 filters (Auth, CSRF, Encoding, SecurityHeader, RateLimit, BodySizeLimit, ContentTypeValidation)
│   │   ├── servlet/         # 39 servlets (Login, SAML, Admin, Modules, Drift, Email, Sync, MCP, etc.)
│   │   ├── model/           # User, Module, Group POJOs
│   │   └── util/            # 22 utilities (AppConfig, DBUtil, SyncExecutor, CryptoUtil, etc.)
│   ├── src/main/webapp/
│   │   ├── WEB-INF/         # web.xml, saml.properties, lib/ (12 JARs)
│   │   ├── shared/          # ic-styles.css, ic-utils.js, ic-icons.css
│   │   ├── modules/         # 16 built-in modules (customer-stats, drift-monitor, utskick, jira, etc.)
│   │   └── *.jsp            # login, register, dashboard, admin, create-module, manage-*
│   ├── docs/                # GETTING_STARTED, API-GUIDE, MODULE-SPEC/GUIDE, SAML-SETUP, KNOWLEDGE-BASE, security audits
│   ├── sql/                 # 35 migration scripts (001-032 + baseline)
│   └── build/               # Compiled classes
├── Ny mapp/                 # PowerShell scripts for stats collection, cert scanning
├── apache-tomcat-9.0.100/   # Embedded Tomcat
├── package-installer.ps1    # Builds WAR + install package
├── install-package/         # Deployable: icDashBoard.war, install.ps1, install.sql
└── .claude/
    ├── rules/               # Domain-specific context (auto-loaded by file path)
    └── skills/              # /new-servlet, /new-module, /new-migration
```

## JAR Dependencies (WEB-INF/lib/) — No Maven/Gradle

| JAR | Purpose |
|-----|---------|
| `mysql-connector-j-8.4.0.jar` | JDBC driver |
| `jbcrypt-0.4.jar` | Password hashing |
| `java-saml-2.9.0.jar` + `java-saml-core-2.9.0.jar` | SAML2 SSO |
| `xmlsec-2.3.4.jar` | XML signature validation |
| `joda-time-2.10.6.jar` | Date/time (SAML dep) |
| `commons-lang3-3.14.0.jar` + `commons-codec-1.16.1.jar` | String utils, encoding |
| `woodstox-core-6.5.0.jar` + `stax2-api-4.2.1.jar` | XML StAX parser |
| `slf4j-api-1.7.36.jar` + `slf4j-simple-1.7.36.jar` | Logging |

When adding JARs, Eclipse WTP may not sync — may need server Clean + Restart.

## Build & Run

1. Eclipse IDE → open workspace pointing to the `claudable` directory
2. MySQL 5.7 running with database `icdashboard`
3. Start Tomcat from Eclipse Servers panel
4. Navigate to `http://<host>:<port>/icDashBoard/`
5. Eclipse auto-compiles on save — no manual build step
6. See `icDashBoard/docs/GETTING_STARTED.md` for full setup guide

**DB credentials**: Stored in `WEB-INF/app-secrets.properties` (not committed to git).
Copy `app-secrets.properties.template` and fill in your values.

**MySQL CLI** (always use utf8mb4):
```
"C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" -u icdashboarduser -p icdashboard --default-character-set=utf8mb4
```

**JDBC URL**: Configured in `app-secrets.properties` key `db.url`

## Critical Coding Conventions

### Java
- Package: `com.infocaption.dashboard.{filter,servlet,model,util}`
- **No JSON library** — all JSON via `StringBuilder` + regex parsing (`extractJsonString`, `extractJsonArray`). Do NOT add Jackson/Gson.
- **Config pattern**: `AppConfig.get("key", FALLBACK_CONSTANT)` for all configurable values. Keep `FALLBACK_` constant as backup.
- Try-with-resources for JDBC. Parameterized SQL queries (prevent injection).
- POJOs implement `Serializable`
- Admin guards: `AdminUtil.requireAdmin(req, resp)` for admin-only APIs
- Servlet init: `CREATE TABLE IF NOT EXISTS` in `init()` for idempotent startup

### Frontend
- `ic-styles.css` design system (CSS variables, BEM-like classes, no Bootstrap/Tailwind)
- **ic-icons font** for toolbar buttons (`icon-flag`, `icon-search`, etc.) — **no emojis for toolbar buttons**
- `postMessage` for iframe ↔ parent communication
- CSRF: global `fetch()` wrapper auto-injects token; multipart forms use query string
- Async IIFE pattern in JS, `fetch()` for API calls
- JSP: `contentType="text/html; charset=UTF-8"`, `request.getContextPath()` for URLs
- JSTL available (`fn:escapeXml`)

### Database
- `utf8mb4_unicode_ci` collation everywhere (emoji support)
- `--default-character-set=utf8mb4` for CLI SQL imports

## Testing

No automated tests. Manual testing via browser + DevTools + Tomcat logs.

## Common Issues

| Issue | Fix |
|-------|-----|
| Emojis show as `?` | JDBC URL needs `connectionCollation=utf8mb4_unicode_ci` |
| Swedish chars garbled in SQL import | CLI must use `--default-character-set=utf8mb4` |
| `NoClassDefFoundError` after adding JARs | Eclipse WTP didn't sync — Clean server or copy to `tmp0/wtpwebapps/` |
| 401 on API calls | Session expired (30min), re-login |
| JSP changes not reflected | Restart Tomcat or copy to `tmp0/wtpwebapps/` |
| SSO fails | Check `saml.properties` + claim URIs in Admin → Inställningar. See `docs/SAML-SETUP.md` |
| AppConfig bootstrap loop | `loading` flag prevents recursion. Check DB connectivity. |
| Admin panel 403 | Set `is_admin = 1`: `UPDATE users SET is_admin = 1 WHERE email = '...'` |

## Domain Guides

Detailed context is in `.claude/rules/` — auto-loaded when working in relevant files:

| Rule file | Content |
|-----------|---------|
| `api-and-servlets.md` | URL routes, servlet patterns, AppConfig, admin system |
| `database-schema.md` | Full schema, migrations, external DB |
| `security-and-auth.md` | Login, SAML SSO, CSRF, groups, security headers |
| `frontend-and-modules.md` | JSP/JS/CSS, 16 modules, widgets, postMessage, iframe patterns |
| `data-sync.md` | SyncExecutor, field transforms, SuperOffice sync jobs |
| `powershell-scripts.md` | Stats collection, certificate monitoring |
| `packaging.md` | WAR build, install package, credential cleaning |

## Skills

| Skill | Purpose |
|-------|---------|
| `/new-servlet` | Scaffold a new API servlet with auth, CSRF, JSON, web.xml |
| `/new-module` | Create a new dashboard module with ic-styles/ic-utils |
| `/new-migration` | Create a numbered SQL migration file |
