---
paths:
  - "icDashBoard/src/main/java/**/filter/**"
  - "icDashBoard/src/main/java/**/Saml*.java"
  - "icDashBoard/src/main/java/**/Login*.java"
  - "icDashBoard/src/main/java/**/Register*.java"
  - "icDashBoard/src/main/webapp/WEB-INF/saml.properties"
  - "icDashBoard/src/main/webapp/login.jsp"
  - "icDashBoard/src/main/webapp/register.jsp"
---

# Security & Authentication

## Password Login
- Session-based auth, 30-minute timeout (web.xml)
- BCrypt (work factor 12) via PasswordUtil
- `session.setAttribute("user", User)` — includes `isAdmin()` flag
- Group IDs loaded via `GroupUtil.refreshSessionGroups()` on login

## AuthFilter
- Guards all protected resources
- API endpoints → 401 JSON; pages → redirect to `/login`
- Import endpoint (`/api/customer-stats/import`) supports API key auth via `X-API-Key` header

## Admin Role
- `users.is_admin` column (TINYINT default 0)
- `AdminUtil.requireAdmin(req, resp)` guard → returns User if admin, sends 401/403 + null otherwise
- All `/api/admin/*` and `/api/sync/*` require admin
- `admin.jsp` redirects non-admins
- Self-demotion protection: cannot remove own admin flag

## SAML2 SSO (Microsoft Entra ID)
- SP-initiated: `/saml/login` → Entra ID → `/saml/acs`
- OneLogin java-saml 2.9.0, configured via `WEB-INF/saml.properties`
- SSO users auto-created with `password = 'SSO_NO_PASSWORD'`
- Display name: givenname+surname → name → displayname → email prefix (fallback chain)
- **All claim URIs configurable** via `app_config`: `sso.emailClaimUri`, `sso.givenNameClaimUri`, `sso.surnameClaimUri`, `sso.nameClaimUri`, `sso.displayNameClaimUri`
- **Department group auto-assignment**: Reads `sso.departmentClaimUri` (default: `department/department`), supports pipe-separated multi-department values (e.g. `Utveckling|Kundvard`), syncs to matching groups via `sso_department` column
- 3-step matching: exact `sso_department` match → name match (links group) → auto-create new group
- Configurable: `sso.autoAssignGroups` (enable/disable), `sso.autoCreateGroups` (auto-create missing)
- See `docs/SAML-SETUP.md` for Azure Entra ID config guide

## CSRF Protection
- `CsrfFilter` implements Synchronizer Token Pattern on POST/PUT/DELETE
- Token generated on session creation, stored in session
- **JSP pages**: Global `fetch()` wrapper auto-injects `X-CSRF-TOKEN` header
- **Multipart forms**: Token via query string (filters can't read multipart body)
- **Iframe pages**: Request token from parent dashboard via `GET_CSRF_TOKEN`/`CSRF_TOKEN` postMessage exchange. Meta tag: `<%= session.getAttribute("csrfToken") != null ? session.getAttribute("csrfToken") : "" %>`

## Security Headers
`SecurityHeaderFilter` adds: `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `X-XSS-Protection`, `Referrer-Policy`, Content-Security-Policy

## HTTPS
- HTTP → HTTPS via `<transport-guarantee>CONFIDENTIAL</transport-guarantee>` in web.xml
- SSL on port 8443 with `*.infocaption.com` wildcard cert

## Group System
- **"Alla" group**: Implicit membership for all users. Cannot edit/delete/leave. Modules assigned to "Alla" visible to everyone.
- **Visible groups** (`is_hidden=0`): Users can self-join/leave via `/api/groups`
- **Hidden groups** (`is_hidden=1`): Not shown to non-members, can't self-join (403). Manual member management only.
- **Module-group assignment**: Many-to-many via `module_groups`. User sees module if in ANY of its groups. No groups = visible to all.
- **Session**: `GroupUtil.refreshSessionGroups()` → `session.setAttribute("userGroupIds", Set<Integer>)`, "Alla" always included

### Default Groups
| Name | Icon | Hidden |
|------|------|--------|
| Alla | &#127760; | No |
| Kundvard | &#128172; | No |
| Utveckling | &#128187; | No |
| Support | &#127911; | No |
| Ledning | &#128084; | Yes |
| IT-sakerhet | &#128272; | Yes |
