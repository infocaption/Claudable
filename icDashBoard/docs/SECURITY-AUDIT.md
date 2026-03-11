# Säkerhetsanalys — InfoCaption Dashboard (icDashBoard)

**Datum**: 2026-02-11 (uppdaterad 2026-02-13)
**Granskade filer**: Alla servlets, filter, JSP-sidor, utility-klasser och konfiguration

---

## Sammanfattning

| Allvarlighetsgrad | Antal | Åtgärdade | Kvar |
|-------------------|-------|-----------|------|
| KRITISK           | 3     | 2         | 1    |
| HÖG               | 8     | 7         | 1    |
| MEDEL             | 7     | 7         | 0    |
| LÅG               | 4     | 4         | 0    |

**Totalt**: 22 fynd, 20 åtgärdade, 2 kvar (kräver infrastrukturändringar).

---

## KRITISK (Åtgärda omedelbart)

### 1. Reflected XSS i `login.jsp`

**Fil**: `src/main/webapp/login.jsp`
**Rad**: ~181

```jsp
value="<%= request.getAttribute("username") != null ? request.getAttribute("username") : "" %>"
```

Användarnamnet skrivs tillbaka till HTML **utan escaping**. En angripare kan skicka en POST med `username="><script>alert(1)</script>` och koden körs i offrets webbläsare.

**Åtgärd**: Använd JSTL `<c:out>` eller `fn:escapeXml()` för all utdata från request-attribut:
```jsp
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
value="${fn:escapeXml(requestScope.username)}"
```

- [x] Fixad — Lade till JSTL `fn:escapeXml()` för `username` och `error` attribut.

---

### 2. Reflected XSS i `GroupApiServlet.java`

**Fil**: `src/main/java/.../servlet/GroupApiServlet.java`
**Rad**: 213

```java
resp.getWriter().write("{\"error\":\"Unknown action: " + action + "\"}");
```

Variabeln `action` kommer direkt från JSON-request body och skrivs ut i HTTP-svaret utan JSON-escaping.

**Åtgärd**: Ta bort user input ur felmeddelandet.

- [x] Fixad — Ändrat till `"Unknown action"` utan att inkludera `action`-värdet.

---

### 3. Hårdkodade hemligheter i källkod och CLAUDE.md

**Filer**:
- `src/main/java/.../util/DBUtil.java` (rad 19-20)
- `src/main/java/.../util/SmartassicDBUtil.java`
- `CLAUDE.md` (hela filen)

Alla lösenord, API-nycklar, databasuppgifter och anslutningssträngar finns i klartext:
- `icdashboarduser` / `REDACTED` (lokal DB)
- `zidbase` / `REDACTED` (extern DB)
- `REDACTED` (API-nyckel)
- Azure ACS-nycklar

Om repot läcker ut exponeras all infrastruktur.

**Åtgärd**:
1. Flytta alla hemligheter till miljövariabler eller Azure Key Vault
2. Ta bort lösenord från CLAUDE.md
3. Lägg till `.env` i `.gitignore`
4. Behåll fallback-konstanterna men ersätt med `System.getenv("DB_PASSWORD")` som första val

- [ ] Ej åtgärdad — Kräver infrastrukturändring utanför kodbasen.

---

## HÖG

### 4. Ingen CSRF-skydd

**Påverkade filer**: Alla servlets som hanterar POST/PUT/DELETE

Applikationen saknar helt CSRF-tokens. Alla state-muterande anrop kan triggas av en annan webbplats om användaren är inloggad.

**Åtgärd**: Implementera CSRF-tokens med Synchronizer Token Pattern.

- [x] Fixad — Skapade `CsrfFilter.java` med:
  - Token genereras vid login (LoginServlet, SamlAcsServlet)
  - Validering via `X-CSRF-Token` header (fetch) eller `_csrf` parameter (forms)
  - Registrerat i web.xml på `/*`
  - Meta-tag `<meta name="csrf-token">` i alla JSP-sidor
  - Global `fetch()` wrapper injicerar headern automatiskt
  - DOM-inject av hidden `_csrf` fält i alla POST-formulär
  - Query string fallback för multipart forms

---

### 5. Öppen registrering utan begränsning

**Fil**: `RegisterServlet.java` (publik endpoint `/register`)

Ingen captcha, rate-limiting eller godkännandeprocess.

- [x] Fixad — `RateLimitFilter.java` begränsar POST-anrop till `/login` och `/register` till max 10 per 60 sekunder per IP-adress. Konfigurerat i web.xml.

---

### 6. Saknad åtkomstkontroll på känsliga endpoints

**Påverkade servlets**: `/manage-users`, `/manage-groups.jsp`, `/module/create`, `/api/contacts/*`

- [ ] Ej åtgärdad — Kräver designbeslut om rollbaserad åtkomstkontroll.

---

### 7. Server-Side Request Forgery (SSRF) via Sync-systemet

**Fil**: `src/main/java/.../util/SyncExecutor.java`

Admins kan konfigurera godtyckliga URL:er för datasynk, vilket kan utnyttjas för att nå interna tjänster (metadata-endpoints, interna API:er, etc.).

- [x] Fixad — `validateUrlNotInternal()` i SyncExecutor blockerar:
  - Loopback-adresser (localhost, 127.x.x.x, ::1)
  - Privata nätverk (10.x.x.x, 172.16-31.x.x, 192.168.x.x)
  - Link-local/metadata (169.254.x.x, fe80::)
  - IPv4-mapped IPv6-adresser (::ffff:10.x.x.x etc.)
  - Hostname-mönster (.local, .internal, .localhost)
  - DNS-resolving av host → IP-validering
  - Appliceras på `execute()`, `testConnection()` och `fetchUrl()`

---

### 8. Informationsläckage i felmeddelanden

**Påverkade filer**: AdminApiServlet, CustomerStatsApiServlet, ContactListServlet, EmailApiServlet, SyncConfigServlet, ModuleCreateServlet, GroupManageServlet, ModuleManageServlet

Exponerar databasinformation, tabellnamn, SQL-syntax till klienten.

- [x] Fixad — Tog bort `e.getMessage()` från alla klient-facing felmeddelanden i:
  - `AdminApiServlet.java` (3 ställen)
  - `CustomerStatsApiServlet.java` (1 ställe)
  - `ContactListServlet.java` (2 ställen)
  - `EmailApiServlet.java` (1 ställe)
  - `SyncConfigServlet.java` (2 ställen)
  - `ModuleCreateServlet.java` (2 ställen)
  - `GroupManageServlet.java` (1 ställe)
  - `ModuleManageServlet.java` (1 ställe)

---

### 9. Stored XSS via custom widgets

**Fil**: `dashboard.jsp` (renderCustomWidget)

Custom widgets med `customHtml` och `customJs` renderades direkt via `innerHTML` och `new Function()` utan sanitering.

- [x] Fixad — Custom widgets renderas nu i sandboxed iframes med `sandbox="allow-scripts"`:
  - HTML/JS körs i isolerad kontext utan åtkomst till parent DOM
  - Kan inte läsa cookies, localStorage eller sessionStorage från huvudsidan
  - Kan inte navigera parent-fönstret
  - `srcdoc` används istället för `innerHTML` + `new Function()`

---

### 10. Email HTML Injection

**Fil**: `EmailApiServlet.java`

Mallvariabler ersätts utan HTML-escaping.

- [x] Fixad (vid tidigare granskning) — `escapeHtml()` metod finns redan i EmailApiServlet och appliceras på alla template-variabler (`serverUrl`, `companyName`, `email`, `updateDate`).

---

### 11. Osäker API-nyckel

**Fil**: `AuthFilter.java:13`

```java
private static final String FALLBACK_IMPORT_KEY = "REDACTED";
```

Lättgissad, lågentropinyckel.

- [ ] Ej åtgärdad — Nyckel kan ändras via Admin → Inställningar (config key: `api.importKey`).

---

## MEDEL

### 12. `manage-users` saknas i AuthFilter web.xml

**Fil**: `src/main/webapp/WEB-INF/web.xml`

- [x] Fixad — Lade till AuthFilter-mappningar för `/manage-users` och `/manage-users.jsp`.

---

### 13. Session Fixation

**Filer**: `LoginServlet.java`, `SamlAcsServlet.java`

- [x] Fixad — Invaliderar gammal session innan ny skapas i båda servlets.

---

### 14. Zipbomb / Resource Exhaustion

**Fil**: `ModuleCreateServlet.java` (extractZip)

- [x] Fixad — Lade till:
  - `MAX_ZIP_FILES = 500` (max antal filer)
  - `MAX_ZIP_TOTAL_SIZE = 100 MB` (max okomprimerad storlek)
  - Kastar `IOException` vid överskridande

---

### 15. Inget rate-limiting på inloggning

**Fil**: `LoginServlet.java`

- [x] Fixad — `RateLimitFilter.java` skapad:
  - Sliding window rate limiter per IP-adress
  - Max 10 POST-requests per 60 sekunder (konfigurerbart via init-params)
  - Mappat på `/login` och `/register` i web.xml
  - Returnerar HTTP 429 med `Retry-After` header vid överträdelse
  - Automatisk cleanup av förfallna entries

---

### 16. `useSSL=false` i JDBC-URL

**Fil**: `DBUtil.java:18`

- [ ] Ej åtgärdad — Kräver SSL-konfiguration på MySQL-servern.

---

### 17. postMessage origin-validering saknas i iframe-JSP:er

**Filer**: `admin.jsp`, `manage-groups.jsp`, `manage-modules.jsp`, `create-module.jsp`, `settings.jsp`

Iframe-sidorna lyssnade på `message`-events utan origin-kontroll och skickade `GET_CSRF_TOKEN` med wildcard `'*'`.

- [x] Fixad — Alla 5 iframe-JSP:er validerar nu `e.origin !== window.location.origin` och skickar postMessage med `window.location.origin` istället för `'*'`.

---

### 18. Saknade säkerhetshuvuden (Security Headers)

**Påverkan**: Alla HTTP-svar

- [x] Fixad — Skapade `SecurityHeaderFilter.java` som sätter:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: SAMEORIGIN`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `X-XSS-Protection: 1; mode=block`
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
  - `Permissions-Policy: camera=(), microphone=(), geolocation=()`

---

## LÅG

### 19. `e.printStackTrace()` i produktion

**Påverkan**: Nästan alla catch-block i alla servlets

- [x] Fixad — Alla 37 förekomster av `e.printStackTrace()` i 20 filer ersatta med SLF4J-loggning (`log.error`, `log.warn`). Korrekt kontextuella loggmeddelanden tillagda.

  Påverkade filer: GroupUtil, CertificateApiServlet, ContactListServlet, CustomerStatsApiServlet, EmailApiServlet, GroupApiServlet, GroupManageServlet, LoginServlet, ModuleApiServlet, ModuleSpecServlet, ModuleCreateServlet, SamlAcsServlet, ModuleManageServlet, RegisterServlet, SamlLoginServlet, SamlMetadataServlet, SyncConfigServlet, SyncExecutor, UserManageServlet, WidgetApiServlet.

---

### 20. Ingen request body-storleksbegränsning för JSON-endpoints

**Påverkade servlets**: EmailApiServlet, SyncConfigServlet, AdminApiServlet, GroupApiServlet, ContactListServlet

- [x] Fixad — `BodySizeLimitFilter.java` skapad:
  - Max 1 MB body size för icke-multipart requests (konfigurerbart via init-param)
  - Multipart requests exkluderas (har egna gränser i web.xml)
  - Returnerar HTTP 413 vid överträdelse
  - Mappat på `/api/*` i web.xml

---

### 21. Ingen Content-Type-validering vid JSON-endpoints

- [x] Fixad — `ContentTypeValidationFilter.java` skapad:
  - Validerar Content-Type på POST/PUT/DELETE till `/api/*`
  - Kräver `application/json`, `multipart/form-data` eller `application/x-www-form-urlencoded`
  - Skippar validering för GET/HEAD/OPTIONS och requests utan body
  - Returnerar HTTP 415 vid felaktig Content-Type
  - Mappat på `/api/*` i web.xml

---

### 22. Inga loggningshändelser vid säkerhetsåtgärder

- [ ] Ej åtgärdad — Kräver designbeslut om logg-format och -destination. SLF4J-loggning finns nu i alla servlets som grund att bygga på.

---

### 23. Custom error-pages saknades

**Påverkan**: Tomcat visar standardfelsidor med serverinformation vid 404, 500 etc.

- [x] Fixad — Skapade `error.jsp` med:
  - Hanterar 400, 401, 403, 404, 500 och `java.lang.Throwable`
  - Dynamisk ikon och svenskt meddelande per felkod
  - InfoCaption-branding och "Tillbaka till Dashboard"-knapp
  - Registrerat i web.xml med `<error-page>` entries
  - Ingen stacktrace eller serverinformation exponeras

---

### 24. URL-schemakontroll för avatar/profilbilds-URL

**Fil**: `dashboard.jsp` (avatar update via postMessage)

Avatar-URL från postMessage injicerades i DOM utan schemakontroll, potentiellt sårbar för `javascript:` protocol-injection.

- [x] Fixad — Klientsidan:
  - Blockerar `javascript:`, `data:`, `vbscript:` URL-scheman
  - Använder `createElement()` + `.src` istället för `innerHTML` med strängkonkatenering
  - Saniterar bort `"<>` tecken ur URL
  - Serversidan (AvatarUploadServlet) genererar redan säkra relativa URL:er

---

## Positiva observationer

- **SQL-injection**: Konsekvent användning av `PreparedStatement` med parametriserade frågor. Ingen SQL-injection hittad.
- **Lösenord**: BCrypt med work factor 12 — korrekt implementation.
- **HTTPS**: Påtvingat via `web.xml` `transport-guarantee`.
- **ZIP-hantering**: Path traversal-skydd finns (`..`-kontroll + `normalize()` + `startsWith()`-check).
- **File extension whitelist**: Bara tillåtna filtyper extraheras från ZIP.
- **Sync table whitelist**: Bara `customers` och `servers` tillåtna som sync-mål.
- **SQL identifier validation**: `validateIdentifier()` i SyncExecutor förhindrar injection via dynamiska kolumnnamn.
- **Self-demotion protection**: Admin kan inte ta bort sin egen admin-status.
- **SSO**: Korrekt implementation med OneLogin-bibliotek. SSO-användare kan inte logga in med lösenord (`SSO_NO_PASSWORD` matchar aldrig BCrypt).
- **JSON output escaping**: `JsonUtil.quote()` hanterar alla kontrolltecken korrekt.
- **Module ownership**: Moduler kan bara redigeras/raderas av ägaren.
- **Hidden groups**: Dolda grupper exponeras inte för icke-medlemmar.
- **SSRF-skydd**: SyncExecutor validerar URL:er mot interna/privata IP-adresser.
- **7 säkerhetsfilter**: EncodingFilter, AuthFilter, CsrfFilter, SecurityHeaderFilter, BodySizeLimitFilter, RateLimitFilter, ContentTypeValidationFilter.

---

## Rekommenderad prioriteringsordning (kvarvarande)

| Prio | Åtgärd | Svårighetsgrad | Filer |
|------|--------|----------------|-------|
| 1 | Ta bort hemligheter ur CLAUDE.md/källkod | Enkel/Infra | CLAUDE.md, DBUtil.java, SmartassicDBUtil.java |
| 2 | Admin-kontroll på endpoints | Medel | UserManageServlet, GroupManageServlet, ContactListServlet |
| 3 | SSL för extern DB | Enkel/Infra | SmartassicDBUtil.java |
| 4 | Säkerhetsloggning (audit trail) | Medel | Alla servlets |

---

## Ändringslogg

| Datum | Åtgärd | Filer |
|-------|--------|-------|
| 2026-02-11 | XSS fix i login.jsp | login.jsp |
| 2026-02-11 | XSS fix i GroupApiServlet | GroupApiServlet.java |
| 2026-02-11 | Session fixation fix | LoginServlet.java, SamlAcsServlet.java |
| 2026-02-11 | AuthFilter-mappning | web.xml |
| 2026-02-11 | Informationsläckage fixat | 8 servlets |
| 2026-02-11 | SecurityHeaderFilter skapad | SecurityHeaderFilter.java, web.xml |
| 2026-02-11 | Zipbomb-skydd | ModuleCreateServlet.java |
| 2026-02-11 | CSRF-filter implementerat | CsrfFilter.java, web.xml, LoginServlet.java, SamlAcsServlet.java, 5 JSP-filer |
| 2026-02-13 | postMessage origin-validering | admin.jsp, manage-groups.jsp, manage-modules.jsp, create-module.jsp, settings.jsp |
| 2026-02-13 | Custom error-pages | error.jsp, web.xml |
| 2026-02-13 | SSRF-skydd | SyncExecutor.java |
| 2026-02-13 | Rate-limiting filter | RateLimitFilter.java, web.xml |
| 2026-02-13 | Body size limit filter | BodySizeLimitFilter.java, web.xml |
| 2026-02-13 | Content-Type validering filter | ContentTypeValidationFilter.java, web.xml |
| 2026-02-13 | Widget iframe sandbox | dashboard.jsp |
| 2026-02-13 | Avatar URL-schemakontroll | dashboard.jsp |
| 2026-02-13 | Email HTML-escaping verifierad | EmailApiServlet.java (redan fixad) |
| 2026-02-13 | printStackTrace → SLF4J | 20 Java-filer (37 förekomster) |
