---
paths:
  - "package-installer.ps1"
  - "install-package/**"
---

# Install Package & Packaging

## Packaging Script (package-installer.ps1)

Builds a WAR from source using `javac` + `jar` (no Maven/Gradle).

```powershell
.\package-installer.ps1                                    # Output to .\install-package\
.\package-installer.ps1 -OutputDir "C:\Releases\v1.0"     # Custom output path
```

**Steps**:
1. Verifies prerequisites (`javac` on PATH, source files, Tomcat lib for servlet-api.jar)
2. Compiles with `javac -source 21 -target 21` (classpath = app JARs + Tomcat JARs)
3. Builds webapp structure: WEB-INF/classes + lib + JSPs + modules + shared + assets
4. **Cleans credentials**: replaces domains, emails, SAML cert, API keys with `CHANGE_ME`/`YOUR_DOMAIN`
5. Injects install-specific login.jsp (tabbed login + register) and register.jsp bridge
6. Packages as `icDashBoard.war` via `jar cf`
7. Copies `install.sql`, `install.ps1`, `README.md` to output

## Install Package (install-package/)

| File | Size | Purpose |
|------|------|---------|
| `icDashBoard.war` | ~6.7 MB | Drop in Tomcat `webapps/` |
| `install.ps1` | ~9 KB | Automated installer (finds MySQL/Tomcat, creates DB, deploys WAR) |
| `install.sql` | ~16 KB | Complete DB setup: tables, groups, modules, widgets, config |
| `README.md` | ~2 KB | Installation instructions (Swedish) |

## Install-specific login.jsp
Tabbed login/register page (register tab default for first-time setup). Differs from live `login.jsp`. The `register.jsp` bridge remaps attribute names (`error`→`regError`, `username`→`regUsername`).

## Credential Cleaning
- `icdashboard.infocaption.com` → `YOUR_DOMAIN:8443`
- `messenger@infocaption.com` → `(konfigureras i Admin)`
- `saml.properties` → descriptive placeholders
- `install.sql` seeds `CHANGE_ME` for secret config values
- Data files excluded

## Maintenance
- If new tables/modules/config keys added → update `install-package/install.sql`
- New JARs in `WEB-INF/lib/` auto-included in WAR
- Changes to live `login.jsp` should be reflected in install template inside `package-installer.ps1`
