# InfoCaption Dashboard

En modulär dashboard-plattform for InfoCaption supportorganisationer. Databasdriven modulhantering, lösenords- och SAML2 SSO-autentisering, gruppbaserad åtkomstkontroll, adminsystem och datasync.

---

## Teknikstack

| Komponent | Version | Detaljer |
|-----------|---------|----------|
| Java | 21 | Eclipse Adoptium |
| Tomcat | 9.0.100 | Servlet 4.0 (ingår i repot) |
| MySQL | 5.7 | utf8mb4, schema `icdashboard` |
| Autentisering | BCrypt + SAML2 | jbcrypt-0.4, OneLogin java-saml 2.9.0 |
| JDBC | MySQL Connector/J 8.4.0 | utf8mb4 via connectionCollation |

Se [GETTING_STARTED.md](docs/GETTING_STARTED.md) for fullständig installationsguide.

---

## Snabbstart

1. Installera Java 21, MySQL 5.7 och Eclipse med WTP
2. Skapa databas och kör migreringsskript (se [GETTING_STARTED.md](docs/GETTING_STARTED.md))
3. Kopiera `WEB-INF/app-secrets.properties.template` till `app-secrets.properties` och fyll i credentials
4. Konfigurera Tomcat i Eclipse och lägg till projektet
5. Starta Tomcat och navigera till `http://<host>:<port>/icDashBoard/`
6. Registrera ett konto och logga in

---

## Arkitektur

```
icDashBoard/
├── src/main/java/com/infocaption/dashboard/
│   ├── filter/          # 7 filters: Auth, CSRF, Encoding, Security Headers, Rate Limit, ...
│   ├── servlet/         # 39 servlets: Login, SAML, Admin, Modules, Sync, Email, ...
│   ├── model/           # User, Module, Group POJOs
│   └── util/            # 22 utilities: AppConfig, DBUtil, SyncExecutor, CryptoUtil, ...
├── src/main/webapp/
│   ├── WEB-INF/
│   │   ├── web.xml      # Servlet/filter-mappningar, multipart, session
│   │   └── lib/         # 12 JAR-beroenden (ingen Maven/Gradle)
│   ├── shared/          # ic-styles.css, ic-utils.js, ic-icons.css, fonts/
│   ├── modules/         # 16 systemmoduler (HTML/CSS/JS i iframes)
│   └── *.jsp            # 10 JSP-sidor (login, dashboard, admin, ...)
├── sql/                 # Migreringsskript (001–025)
└── docs/                # GETTING_STARTED, API-GUIDE, SAML-SETUP, MODULE-GUIDE, ...
```

---

## Modulsystem

Moduler laddas som iframes i `dashboard.jsp`. Modulerna är ren HTML/CSS/JS (inte JSP).

### Modultyper

| Typ | Beskrivning | Synlighet |
|-----|-------------|-----------|
| **system** | Förinstallerade | Alla (eller gruppbaserat) |
| **private** | Skapade av användare | Bara skaparen |
| **shared** | Delade av användare | Alla inloggade |

### Systemmoduler (16 st)

| Modul | Kategori |
|-------|----------|
| customer-stats | analytics |
| server-list | analytics |
| utskick | communication |
| certificates | monitoring |
| drift-monitor | monitoring |
| drift-ops | monitoring |
| cloudguard-monitor | monitoring |
| backup-status | monitoring |
| incidents | monitoring |
| jira | tools |
| sql-builder | tools |
| toolbox | tools |
| guide-planner | tools |
| trigger-builder | tools |
| docs | tools |
| pong | fun |

---

## Autentisering

Två inloggningsmetoder:

1. **Lösenord** — BCrypt (work factor 12) mot MySQL
2. **SAML2 SSO** — Microsoft Entra ID (se [SAML-SETUP.md](docs/SAML-SETUP.md))

### Skyddade resurser (AuthFilter)

`/dashboard.jsp`, `/modules/*`, `/api/*`, `/module/*`, `/admin.jsp`, `/manage-*.jsp`, `/settings.jsp`, `/group/*`

### Publika resurser

`/login`, `/register`, `/shared/*`, `/assets/*`, `/saml/*`

---

## API-endpoints

| URL | Servlet | Beskrivning |
|-----|---------|-------------|
| `/login` | LoginServlet | Inloggning |
| `/register` | RegisterServlet | Registrering |
| `/logout` | LogoutServlet | Utloggning |
| `/saml/login` | SamlLoginServlet | SAML SSO-initiering |
| `/saml/acs` | SamlAcsServlet | SAML assertion consumer |
| `/saml/metadata` | SamlMetadataServlet | SP-metadata |
| `/api/modules` | ModuleApiServlet | Modulregister |
| `/api/admin/*` | AdminApiServlet | Admin-API |
| `/api/customer-stats/*` | CustomerStatsApiServlet | Kundstatistik |
| `/api/servers/*` | ServerListApiServlet | Serverlista |
| `/api/email/*` | EmailApiServlet | E-postutskick |
| `/api/contacts/*` | ContactListServlet | Kontaktlista |
| `/api/sync/*` | SyncConfigServlet | Datasync-konfiguration |
| `/api/certificates/*` | CertificateApiServlet | SSL-certifikat |
| `/api/widgets` | WidgetApiServlet | Dashboard-widgets |
| `/api/groups` | GroupApiServlet | Grupphantering |
| `/api/drift/*` | DriftMonitorApiServlet | Driftövervakning |
| `/api/cloudguard/*` | CloudGuardApiServlet | CloudGuard-monitor |
| `/api/incidents/*` | IncidentApiServlet | Incidenthantering |
| `/api/backups` | BackupStatusApiServlet | Backupstatus |
| `/api/jira/*` | JiraApiServlet | Jira-integration |
| `/api/guide-planner/*` | GuidePlannerApiServlet | Guideplanering |
| `/api/tokens/*` | ApiTokenServlet | API-tokens |
| `/api/notifications/*` | NotificationServlet | Notifikationer |
| `/api/mcp` | McpGatewayServlet | MCP Gateway |
| `/api/preferences` | UserPreferencesApiServlet | Användarpreferenser |
| `/api/user/settings` | UserSettingsServlet | Kontoinställningar |
| `/api/user/avatar` | AvatarUploadServlet | Profilbild |
| `/api/version` | VersionServlet | Appversion |

Se [API-GUIDE.md](docs/API-GUIDE.md) for detaljer om autentisering och användning.

---

## Filter-kedja

| Filter | URL | Syfte |
|--------|-----|-------|
| EncodingFilter | `/*` | UTF-8 på alla requests/responses |
| AuthFilter | skyddade resurser | Session-validering, 401 för API |
| CsrfFilter | `/*` | CSRF-token-validering |
| SecurityHeaderFilter | `/*` | CSP, X-Frame-Options, etc. |
| BodySizeLimitFilter | `/api/*` | Begränsar request body-storlek |
| RateLimitFilter | `/login`, `/register`, `/api/cloudguard/*` | Brute force-skydd |
| ContentTypeValidationFilter | `/api/*` | Validerar Content-Type |

---

## JAR-beroenden (WEB-INF/lib/)

| JAR | Syfte |
|-----|-------|
| mysql-connector-j-8.4.0.jar | JDBC-drivrutin |
| jbcrypt-0.4.jar | BCrypt-hashning |
| java-saml-2.9.0.jar + java-saml-core-2.9.0.jar | SAML2 SSO |
| xmlsec-2.3.4.jar | XML-signaturvalidering |
| joda-time-2.10.6.jar | Datum/tid (SAML-beroende) |
| commons-lang3-3.14.0.jar + commons-codec-1.16.1.jar | Strängverktyg, encoding |
| woodstox-core-6.5.0.jar + stax2-api-4.2.1.jar | XML StAX-parser |
| slf4j-api-1.7.36.jar + slf4j-simple-1.7.36.jar | Loggning |

> Inget Maven/Gradle. JARs hanteras manuellt.

---

## Databas

Credentials konfigureras i `WEB-INF/app-secrets.properties` (gitignored).

JDBC URL-format:
```
jdbc:mysql://<host>:3306/icdashboard?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Stockholm&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
```

> `connectionCollation=utf8mb4_unicode_ci` krävs for korrekt emoji-stöd i MySQL 5.7.

Migreringsskript finns i `sql/` (001–025). For fullständigt schema, se installationsskriptet `install-package/install.sql`.

---

## Felsökning

| Problem | Lösning |
|---------|---------|
| Tomcat startar inte | Kontrollera JDK 21-sökväg och att porten är ledig |
| `ClassNotFoundException` | Server → Clean i Eclipse. Kontrollera JARs i `WEB-INF/lib/` |
| Login fungerar inte | Kontrollera att MySQL körs och att tabellerna finns |
| Emojis visas som `?` | JDBC URL måste ha `connectionCollation=utf8mb4_unicode_ci` |
| Svenska tecken trasiga | MySQL CLI: `--default-character-set=utf8mb4` |
| JSP-ändringar syns inte | Starta om Tomcat eller Server → Clean |
| 401 från API | Sessionen har gått ut (30 min). Logga in igen. |
| Admin-panel syns inte | `UPDATE users SET is_admin = 1 WHERE email = '...'` |
| SSO fungerar inte | Se [SAML-SETUP.md](docs/SAML-SETUP.md) |

---

## Dokumentation

| Dokument | Innehåll |
|----------|----------|
| [GETTING_STARTED.md](docs/GETTING_STARTED.md) | Fullständig installationsguide |
| [API-GUIDE.md](docs/API-GUIDE.md) | API-endpoints, tokens, exempel |
| [MODULE-GUIDE.md](docs/MODULE-GUIDE.md) | Skapa egna moduler |
| [MODULE-SPEC.md](docs/MODULE-SPEC.md) | Teknisk modulspecifikation |
| [SAML-SETUP.md](docs/SAML-SETUP.md) | SSO med Microsoft Entra ID |
| [KNOWLEDGE-BASE.md](docs/KNOWLEDGE-BASE.md) | Kunskapsbas / MCP-integration |

---

## Changelog

### v4.0 — Admin, SSO, Sync, Säkerhet
- Adminsystem med rollbaserad åtkomst
- SAML2 SSO (Microsoft Entra ID)
- Gruppbaserad modulsynlighet
- Datasync med extern databas (SuperOffice)
- E-postutskick via Azure Communication Services
- Dashboard-widgets med anpassningsbar layout
- 16 systemmoduler (drift, certifikat, CloudGuard, Jira, ...)
- CSRF-skydd, rate limiting, security headers
- API-tokens for extern integration
- MCP Gateway

### v3.0 — Databasdrivet modulsystem
- Modulregister i MySQL istället for hårdkodat JavaScript
- System-, privata- och delade moduler
- Moduluppladdning (.html/.zip, max 50MB)
- AI-specifikationstext per modul

### v2.0 — Java JSP-konvertering
- Konverterad från statisk HTML till Java Servlet/JSP
- BCrypt-autentisering mot MySQL
- AuthFilter + EncodingFilter

### v1.0 — Initial release
- Dashboard, Kundstatistik, SQL Builder, Verktygslåda
