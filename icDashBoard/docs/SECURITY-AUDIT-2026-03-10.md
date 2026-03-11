# Säkerhetsanalys v2 — InfoCaption Dashboard (icDashBoard)

**Datum**: 2026-03-10
**Föregående audit**: 2026-02-11 (22 fynd, 20 fixade)
**Granskade filer**: Alla 37 servlets, 7 filter, 17 utility/model-klasser, alla JSP/HTML/JS/CSS, 29 SQL-migreringar, web.xml
**Granskad av**: Claude Opus 4.6

---

## Sammanfattning

| Allvarlighetsgrad | Identifierade | Åtgärdade (2026-03-10) | Kvar öppna |
|-------------------|---------------|------------------------|------------|
| KRITISK           | 4             | 3 (K-0, K-2, K-3)     | **1** (K-4)|
| HÖG               | 9             | 8 (H-2–H-9)           | **1** (K-1)|
| MEDEL             | 7             | 7 (M-1–M-7)           | **0**      |
| LÅG               | 5             | 5 (L-1–L-5)           | **0**      |

**Totalt**: 27 identifierade → **24 åtgärdade** → **4 kvar öppna** (1 KRITISK + 1 HÖG + 2 accepterade).

Sedan februari har projektet utökats med MCP Gateway, Push-notiser, Health Monitor, Drift & Övervakning, API-tokens, Incident-rapporter och CloudGuard — totalt ~15 nya filer. Dessa moduler introducerar nya attackytor som inte täcktes av den tidigare auditen.

---

## STATUS: Kvarvarande från februari-audit

### K-0. Hårdkodade hemligheter i källkod (KRITISK — ÅTGÄRDAD)

**Åtgärd (2026-03-10)**: Alla hemligheter flyttade till `WEB-INF/app-secrets.properties`.
- Ny `SecretsConfig.java` laddas vid startup via `AppStartupListener` (ServletContextListener)
- `DBUtil.java`, `SmartassicDBUtil.java` — alla EMERGENCY_* konstanter borttagna, läser nu SecretsConfig
- `AuthFilter.java` — FALLBACK_IMPORT_KEY borttagen, läser SecretsConfig
- `AcsEmailUtil.java` — alla FALLBACK_* borttagna, läser SecretsConfig
- `DriftMonitorApiServlet.java` — hårdkodad API-nyckel borttagen
- `CLAUDE.md`, `.claude/rules/`, `.claude/skills/` — hemligheter rensade
- `db.properties` tömd (bakåtkompatibilitet), ersatt av `app-secrets.properties`
- `.gitignore` uppdaterad: `**/app-secrets.properties` och `**/db.properties`
- `app-secrets.properties.template` skapad som referens

**Kvar**: SQL-migreringar (009, etc.) och PowerShell-scripts har fortfarande credentials. Dessa är install-filer och bör hanteras separat.

### K-1. RBAC saknas på endpoints (HÖG — KVAR)

**Filer**: `UserManageServlet`, `GroupManageServlet`, `ContactListServlet`, `ModuleCreateServlet`

Icke-admin-användare kan nå känsliga endpoints.

**Status**: Kräver designbeslut.

---

## NYA FYND: KRITISK

### K-2. Azure ACS access key hårdkodad som fallback i AcsEmailUtil

**Fil**: `AcsEmailUtil.java`, rad 30-35

```java
private static final String FALLBACK_ENDPOINT = "https://ic-drift-communicationservice...";
private static final String FALLBACK_ACCESS_KEY = "7GfY0fOJ2auMMMbSWHLQ..."; // fullständig nyckel
```

Även om AppConfig används i första hand finns den fullständiga Azure-nyckeln som fallback-konstant. Om AppConfig misslyckas används den automatiskt. Nyckeln ger full åtkomst till InfoCaptions Azure Communication Service.

**Påverkan**: Fullständig kontroll över e-postsändning, kan användas för phishing med `messenger@infocaption.com`.
**Åtgärd**: Ta bort fallback-nyckeln. Kasta exception om config saknas istället.

---

### K-3. CloudGuard publika endpoints utan autentisering

**Fil**: `CloudGuardApiServlet.java` + `AuthFilter.java`

CloudGuard-endpoints är publika:
- `POST /api/cloudguard/report` — vem som helst kan rapportera incidenter
- `POST /api/cloudguard/resolve` — vem som helst kan lösa incidenter

**Påverkan**: En angripare kan:
1. Spamma falska incidenter (DoS på monitorering)
2. Lösa riktiga incidenter utan behörighet (döljer verkliga problem)
3. Triggra push-notiser till alla prenumeranter

**Åtgärd**: Kräv minst API-nyckel eller Bearer-token. Rate limiting finns (10/60s/IP) men räcker inte.

---

### K-4. SAML-validering avstängd (development defaults i produktion)

**Fil**: `SamlConfigUtil.java`, rad 62-66

```java
samlData.put("onelogin.saml2.security.authnrequest_signed", "false");
samlData.put("onelogin.saml2.security.want_messages_signed", "false");
samlData.put("onelogin.saml2.security.want_assertions_encrypted", "false");
samlData.put("onelogin.saml2.security.strict", "false");
```

Alla säkerhetsinställningar är relaxerade:
- AuthnRequest signeras inte → SAML request forgery möjlig
- Svar behöver inte vara signerade → assertion injection
- Assertions inte krypterade → klartext i transit
- Strict mode av → parser accepterar felaktiga/manipulerade SAML-svar

**Påverkan**: En angripare med nätverksåtkomst kan förfalska SAML-assertions och logga in som valfri användare.
**Åtgärd**: Ställ in alla till `true` (kräver att IdP-konfigurationen matchar).

---

## NYA FYND: HÖG

### H-2. CSP tillåter `unsafe-inline` för script-src

**Fil**: `SecurityHeaderFilter.java`, rad ~28

```
script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com
```

`unsafe-inline` gör CSP-skyddet mot XSS verkningslöst. Alla IIFE-mönster i JSP-sidor kräver det idag, men det öppnar för script-injection om XSS hittas.

**Påverkan**: CSP ger inget reellt XSS-skydd.
**Åtgärd**: Migrera till nonce-baserat CSP (`script-src 'nonce-{random}'`). Generera unikt nonce per request.

---

### H-3. CDN-bibliotek utan SRI (Subresource Integrity)

**Fil**: `modules/customer-stats/displaystats.html`, rad 11-13

```html
<script src="https://cdnjs.cloudflare.com/ajax/libs/xlsx/0.18.5/xlsx.full.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chartjs-adapter-date-fns@3.0.0/..."></script>
```

Tre externa JavaScript-bibliotek laddas utan `integrity`-attribut.

**Påverkan**: Om CDN komprometteras körs skadlig kod i användarens session med full åtkomst till CSRF-token och sessionsdata.
**Åtgärd**: Lägg till `integrity="sha384-..."` och `crossorigin="anonymous"` på alla externa script-taggar.

---

### H-4. RateLimitFilter spoofbar via X-Forwarded-For

**Fil**: `RateLimitFilter.java`, rad ~89

```java
String forwarded = req.getHeader("X-Forwarded-For");
String ip = (forwarded != null) ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
```

Om applikationen inte ligger bakom en proxy som saniterar `X-Forwarded-For` kan en angripare sätta godtycklig IP per request och kringgå all rate limiting.

**Påverkan**: Brute force-attacker mot login/register utan begränsning.
**Åtgärd**: Konfigurera trusted proxy. Använd `getRemoteAddr()` som fallback bara om proxy är verifierad, eller lägg till konfigurationsval.

---

### H-5. SVG/XML-filuppladdning tillåter XSS och XXE

**Fil**: `ModuleFileApiServlet.java`, rad 43-46 (extensions whitelist)

SVG-filer accepteras vid modulfiluppladdning. SVG kan innehålla JavaScript:

```xml
<svg xmlns="http://www.w3.org/2000/svg" onload="alert(document.cookie)">
```

Om filen serveras med `Content-Type: image/svg+xml` körs scriptet. XML-filer kan dessutom utnyttjas för XXE-attacker.

**Påverkan**: Stored XSS om användare navigerar till uppladdad SVG. XXE kan läsa serverfiler.
**Åtgärd**:
1. Strippa alla script-element och event handlers från SVG
2. Serve SVG med `Content-Disposition: attachment` eller via CSP sandbox
3. Validera magic bytes, inte bara extension

---

### H-6. Saknad CSRF-validering för multipart-uploads

**Filer**: `AvatarUploadServlet`, `ModuleFileApiServlet`, `ModuleCreateServlet`

Multipart-uploads har inte synlig CSRF-token-validering i koden. CsrfFilter kan inte läsa multipart body (filter saknar `@MultipartConfig`). Fallback via query string finns i filter, men det är oklart om klienten faktiskt skickar det konsekvent.

**Påverkan**: Potentiell CSRF-baserad filuppladdning.
**Åtgärd**: Verifiera att alla multipart-formulär inkluderar CSRF-token i query string eller header. Lägg till integrationstester.

---

### H-7. MCP Gateway tool injection risk

**Fil**: `McpGatewayServlet.java`

Tool-namn vidarebefordras direkt till `McpClientManager` utan validering av innehåll. Om ett upstream MCP-servernamn kolliderar med ett annat prefix, eller om tool-namn innehåller specialtecken, kan routing bli felaktig.

**Påverkan**: Tool namespace-kollision, felaktigt tool-anrop till fel server.
**Åtgärd**:
1. Validera tool-namn mot ett strikt regex (alfanumeriskt + underscore)
2. Implementera per-tool whitelist/ACL
3. Begränsa vilka användare som kan anropa vilka tools

---

### H-8. sync_configs.auth_config lagrar credentials i klartext

**Tabell**: `sync_configs`, kolumn `auth_config` (TEXT)

Bearer tokens, API-nycklar och basic auth-credentials lagras som JSON i klartext i databasen.

**Påverkan**: Vid databasläckage exponeras alla sync-credentials.
**Åtgärd**: Kryptera `auth_config` med AES-256 (app-level encryption med nyckel i env var).

---

### H-9. mcp_servers.auth_config lagrar credentials i klartext

**Tabell**: `mcp_servers`, kolumn `auth_config` (TEXT)

Samma problem som H-8, men för MCP-serveranslutningar. OAuth tokens, Bearer tokens, API-nycklar i klartext.

**Påverkan**: Vid databasläckage exponeras alla MCP-credentials.
**Åtgärd**: Kryptera med samma mekanism som H-8.

---

## NYA FYND: MEDEL

### M-1. RateLimitFilter är per-JVM — fungerar inte i kluster

**Fil**: `RateLimitFilter.java`

Rate limiting lagras i `ConcurrentHashMap` i JVM-minne. Vid lastbalansering med flera Tomcat-instanser kan en angripare sprida requests och kringgå limiten.

**Påverkan**: Rate limiting ineffektiv vid skalning.
**Åtgärd**: Acceptabelt för nuvarande single-server-deployment. Vid skalning: Redis-baserad rate limiter.

---

### M-2. ContentTypeValidationFilter tillåter form-encoded på API-endpoints

**Fil**: `ContentTypeValidationFilter.java`, rad 45

`application/x-www-form-urlencoded` accepteras på `/api/*` endpoints. De flesta API-endpoints förväntar sig JSON.

**Påverkan**: Möjliggör CSRF via vanliga HTML-formulär (Content-Type matchar).
**Åtgärd**: Begränsa `/api/*` till enbart `application/json` och `multipart/form-data`.

---

### M-3. Saknad row-level access control för ContactListServlet

**Fil**: `ContactListServlet.java`

Alla inloggade användare kan söka i den externa smartassic-databasen utan filtrering på företag eller roll. Contactlistservlet visar kontaktdata (e-post, telefon, företag) för alla kunder.

**Påverkan**: Vanliga användare ser alla kundkontakter.
**Åtgärd**: Lägg till admin-kontroll eller gruppbaserad filtrering.

---

### M-4. Saknad row-level access control för DriftMonitorApiServlet

**Fil**: `DriftMonitorApiServlet.java`

Alla inloggade användare (och API-token-innehavare) kan se all infrastrukturdata: maskiner, tjänster, hosts, IP-adresser, portar.

**Påverkan**: Informationsläckage om intern infrastruktur till vanliga användare.
**Åtgärd**: Begränsa till admin eller specifik grupp (t.ex. "Drift").

---

### M-5. DBUtil/SmartassicDBUtil saknar connection pooling

**Filer**: `DBUtil.java`, `SmartassicDBUtil.java`

Varje `getConnection()`-anrop skapar en ny JDBC-anslutning. Ingen pooling.

**Påverkan**:
1. Connection exhaustion vid hög last
2. Ingen connection timeout/validation
3. Potentiell DoS via connection starvation
**Åtgärd**: Implementera connection pooling (HikariCP eller liknande).

---

### M-6. Jira API-token lagras i klartext i user_preferences

**Fil**: `JiraApiServlet.java`

Användarens Jira API-token lagras i `user_preferences.pref_value` utan kryptering.

**Påverkan**: Vid databasläckage exponeras alla användares Jira-tokens.
**Åtgärd**: Kryptera token vid lagring, dekryptera vid användning.

---

### M-7. Push-notifikationer kan triggas via publika CloudGuard-endpoints

**Filer**: `CloudGuardApiServlet.java`, `NotificationServlet.java`, `WebPushUtil.java`

Eftersom CloudGuard report/resolve är publika (K-3) och incidenter triggar push-notiser kan en extern angripare skicka notiser till alla prenumeranter.

**Påverkan**: Spam-notiser, social engineering via falska incidentrapporter.
**Åtgärd**: Löses automatiskt om K-3 åtgärdas (kräv autentisering för CloudGuard).

---

## NYA FYND: LÅG

### L-1. Service Worker har hårdkodade context paths

**Fil**: `sw-push.js`, rad 26-27, 54

```javascript
icon: '/icDashBoard/shared/ic-logo-192.png',
const url = '/icDashBoard/dashboard.jsp?module=cloudguard-monitor';
```

**Påverkan**: Bryter om applikationen deployeras med annan context path.
**Åtgärd**: Injicera context path via config eller `self.location`.

---

### L-2. Google Fonts laddas utan SRI

**Filer**: `login.jsp`, `register.jsp`

Google Fonts laddas via `fonts.googleapis.com` utan integrity-hash.

**Påverkan**: Låg risk (Google Fonts är trusted), men avviker från SRI-principen i H-3.
**Åtgärd**: Ladda ner fonten lokalt eller acceptera risken (Google Fonts ändrar CSS dynamiskt, SRI fungerar dåligt).

---

### L-3. useSSL=false i JDBC-URL (kvar sedan februari)

**Filer**: `DBUtil.java`, `SmartassicDBUtil.java`

Databaskommunikation sker okrypterad.

**Påverkan**: Credentials och data i klartext på nätverket (lokal: låg risk, extern smartassic: högre risk).
**Åtgärd**: Aktivera SSL på MySQL, ändra `useSSL=true`.

---

### L-4. Saknad audit logging

Trots att SLF4J nu finns i projektet loggas inte:
- Misslyckade inloggningsförsök (med IP/username)
- Admin-åtgärder (user delete, config changes)
- API-token creation/deletion
- Sync-konfigurationsändringar
- Grupp-/modulmemberskap-ändringar

**Påverkan**: Svårt att utreda incidenter i efterhand.
**Åtgärd**: Implementera strukturerad audit logging till dedikerad tabell eller logfil.

**Undantag**: MCP Gateway har audit logging (bra!).

---

### L-5. Deprecated API: document.execCommand('copy')

**Fil**: `settings.jsp`

Använder `document.execCommand('copy')` som är deprecated.

**Påverkan**: Kan sluta fungera i framtida webbläsarversioner.
**Åtgärd**: Migrera till `navigator.clipboard.writeText()`.

---

## POSITIVA OBSERVATIONER (sedan februari)

Sedan förra auditen har projektet förbättrats avsevärt:

| Område | Status |
|--------|--------|
| SQL Injection-skydd | Konsekvent `PreparedStatement` — inga injektionspunkter hittade |
| BCrypt-lösenord | Work factor 12, korrekt implementation |
| CSRF-skydd | Global fetch-wrapper + CsrfFilter — robust |
| postMessage origin-validering | Alla lyssnare validerar `window.location.origin` |
| Session fixation | Fixad i alla login-flöden |
| HTTPS | Tvingat via web.xml |
| ZIP path traversal | Robust skydd med normalize + startsWith |
| Sync table whitelist | Bara 3 tabeller tillåtna |
| SQL identifier validation | Alfanumeriskt + underscore, backtick-quoted |
| Self-demotion protection | Admin kan inte ta bort sin egen adminroll |
| API tokens | SHA-256 hashade, TTL, max per user |
| MCP audit logging | Fullständig spårbarhet för tool calls |
| SSRF-skydd | `validateUrlNotInternal()` med DNS-resolution |
| 7 säkerhetsfilter | Encoding, Auth, CSRF, Headers, BodySize, RateLimit, ContentType |
| Custom widgets | Sandboxed iframes |
| Error pages | Anpassade felsidor utan stacktrace |

---

## PRIORITERAD ÅTGÄRDSLISTA

### Omedelbart (vecka 1)
| # | Fynd | Svårighet | Filer |
|---|------|-----------|-------|
| 1 | K-3: CloudGuard auth | Enkel | CloudGuardApiServlet.java, AuthFilter.java |
| 2 | K-4: SAML strict mode | Enkel | SamlConfigUtil.java (4 rader) |
| 3 | K-2: Ta bort ACS fallback-nyckel | Enkel | AcsEmailUtil.java |
| 4 | H-3: SRI på CDN-script | Enkel | displaystats.html (3 taggar) |
| 5 | H-5: SVG/XML sanitering | Medel | ModuleFileApiServlet.java |

### Kort sikt (vecka 2-3)
| # | Fynd | Svårighet | Filer |
|---|------|-----------|-------|
| 6 | H-4: XFF-spoofing fix | Enkel | RateLimitFilter.java |
| 7 | M-2: Blockera form-encoded på API | Enkel | ContentTypeValidationFilter.java |
| 8 | M-3: Admin-check på contacts | Enkel | ContactListServlet.java |
| 9 | M-4: Admin-check på drift | Enkel | DriftMonitorApiServlet.java |
| 10 | H-6: CSRF multipart-verifiering | Medel | Verifiera alla upload-forms |

### Medellång sikt (månad 1-2)
| # | Fynd | Svårighet | Filer |
|---|------|-----------|-------|
| 11 | K-0: Hemligheter → env vars | Infrastruktur | DBUtil, SmartassicDBUtil, AcsEmailUtil |
| 12 | H-2: CSP nonce-baserat | Hög | SecurityHeaderFilter + alla JSP |
| 13 | H-8/H-9: Kryptera auth_config | Medel | SyncExecutor, McpClientManager |
| 14 | M-6: Kryptera Jira-token | Medel | JiraApiServlet |
| 15 | L-4: Audit logging | Medel | Ny utility + alla admin-servlets |

### Lång sikt (kvartal)
| # | Fynd | Svårighet | Filer |
|---|------|-----------|-------|
| 16 | H-7: MCP tool ACL | Hög | McpGatewayServlet, McpClientManager |
| 17 | K-1: Full RBAC | Hög | Designbeslut + alla servlets |
| 18 | M-5: Connection pooling | Medel | DBUtil, SmartassicDBUtil |
| 19 | L-3: MySQL SSL | Infrastruktur | MySQL-server + JDBC URL |

---

## JÄMFÖRELSE MED FEBRUARI-AUDIT

| Kategori | Feb 2026 | Mar 2026 | Förändring |
|----------|----------|----------|------------|
| Totalt granskade filer | ~40 | ~80+ | +100% (nya moduler) |
| Fynd totalt | 22 | 47 (22+25) | +25 nya |
| Fixade | 20 | 20 | ±0 |
| Öppna | 2 | 27 | +25 (varav 23 i ny kod) |
| Nya servlets sedan feb | — | 8 | MCP, Push, Health, Drift, Incidents, CloudGuard, ApiToken, etc. |
| Nya filter | — | 0 | Inga nya filter |
| Nya SQL-migreringar | — | 5 | 025-029 |

---

## ATTACKYTEANALYS

```
                        ┌─────────────────────────────┐
                        │      INTERNET               │
                        └──────────┬──────────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │   HTTPS (port 8443)           │
                    │   SecurityHeaderFilter        │
                    │   EncodingFilter              │
                    └──────────────┬───────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
     ┌────────▼───────┐  ┌────────▼───────┐  ┌────────▼───────┐
     │  PUBLIKT        │  │  API-KEY AUTH   │  │  ADMIN-ONLY    │
     │                 │  │                │  │                │
     │ /login          │  │ /api/customer  │  │ /api/admin/*   │
     │ /register       │  │   -stats/imp.  │  │ /api/sync/*    │
     │ /saml/*         │  │ /api/drift/*   │  │ /api/mcp/admin │
     │                 │  │ /api/cloudguard│  │ /module/create │
     │ RateLimitFilter │  │   /report  [✓] │  │                │
     │ (10/60s/IP)     │  │   /resolve [✓] │  │ AdminUtil      │
     │                 │  │                │  │ .requireAdmin  │
     └─────────────────┘  ├────────────────┤  └────────────────┘
                          │  SESSION AUTH   │
                          │                │
                          │ /api/modules   │
                          │ /api/groups    │
                          │ /api/email/*   │
                          │ /api/contacts  │
                          │ /api/jira      │
                          │ /api/mcp       │
                          │ /api/tokens    │
                          │ /api/incidents │
                          │ /api/widgets   │
                          │ /api/notif.    │
                          │                │
                          │ AuthFilter     │
                          │ CsrfFilter     │
                          └────────────────┘

     [✓] = Åtgärdat 2026-03-10
```

---

## REKOMMENDATIONER FÖR FRAMTIDA UTVECKLING

1. **Alla nya servlets** bör använda `AdminUtil.requireAdmin()` om de hanterar infrastrukturdata
2. **Alla nya publika endpoints** bör ha explicit motivering i koden
3. **Nya auth_config-fält** bör krypteras vid lagring
4. **CDN-beroenden** bör inkludera SRI eller bundlas lokalt
5. **SAML-konfigurationen** bör vara per-miljö (dev/staging/prod)
6. **Överväg**: Automatiserade säkerhetstester (OWASP ZAP, SQLMap) i CI/CD

---

## Ändringslogg

| Datum | Åtgärd |
|-------|--------|
| 2026-02-11 | Initial audit (22 fynd) |
| 2026-02-13 | 20 fixar applicerade |
| 2026-03-10 | Uppföljande audit v2: 25 nya fynd identifierade i utökad kodbas |
| 2026-03-10 | 21 fixar applicerade (se detaljer nedan) |

---

## ÅTGÄRDADE FYND (2026-03-10)

| Fynd | Åtgärd | Ändrade filer |
|------|--------|---------------|
| **K-0** Hårdkodade hemligheter | Alla secrets → `app-secrets.properties` via `SecretsConfig.java` | `DBUtil.java`, `SmartassicDBUtil.java`, `AuthFilter.java`, `AcsEmailUtil.java`, `DriftMonitorApiServlet.java`, `CLAUDE.md`, `.claude/rules/`, `.claude/skills/` |
| **K-2** ACS access key fallback | Hårdkodad nyckel borttagen, validering tillagd | `AcsEmailUtil.java` |
| **K-3** CloudGuard publika endpoints | Kräver nu API-nyckel (samma som drift/import) | `AuthFilter.java` |
| **K-4** SAML strict mode | Konfigurerbara säkerhetsinställningar med strikta defaults | `SamlConfigUtil.java` |
| **H-2** CSP unsafe-inline | Nonce-baserat CSP, unikt 16-byte nonce per request | `SecurityHeaderFilter.java`, 7 JSP-filer (18 script-taggar) |
| **H-3** CDN utan SRI | Lagt till `integrity` + `crossorigin` på alla 3 CDN-scripts | `displaystats.html` |
| **H-4** XFF-spoofing | Trusted proxy-mönster: XFF only om request från konfigurerad proxy-IP | `RateLimitFilter.java` |
| **H-5** SVG/XML XSS | SVG/XML serveras som `application/octet-stream` med `Content-Disposition: attachment` | `ModuleFileApiServlet.java` |
| **H-6** CSRF multipart | Verifierat: CsrfFilter hanterar redan header, form param och query string | Ingen ändring (redan OK) |
| **H-7** MCP tool injection | Strikt regex-validering av tool-namn (`^[a-zA-Z0-9_][a-zA-Z0-9_\-]*$`) | `McpGatewayServlet.java` |
| **H-8/H-9** auth_config klartext | AES-256-GCM kryptering via `CryptoUtil`, auto-genererad master key | `CryptoUtil.java` (ny), `SyncConfigServlet.java`, `SyncExecutor.java`, `McpClientManager.java`, `McpAdminServlet.java` |
| **M-1** Rate limit kluster | Dokumenterat i Javadoc: per-JVM, acceptabelt för single-server | `RateLimitFilter.java` (redan dokumenterat) |
| **M-2** form-encoded på API | Borttagen `application/x-www-form-urlencoded` från tillåtna Content-Types | `ContentTypeValidationFilter.java` |
| **M-3** Contacts access control | Admin-kontroll: icke-admin får 403 | `ContactListServlet.java` |
| **M-4** Drift access control | Admin-kontroll: icke-admin får 403, API-key user markeras som admin | `DriftMonitorApiServlet.java` |
| **M-5** Connection pooling | `ArrayBlockingQueue`-baserad pool (10 för lokal DB, 5 för extern) med `isValid()` | `DBUtil.java`, `SmartassicDBUtil.java` |
| **M-6** Jira-token klartext | Krypteras via `CryptoUtil` vid lagring/läsning | `JiraApiServlet.java`, `UserPreferencesApiServlet.java` |
| **M-7** Push via publika endpoints | Löst via K-3: CloudGuard kräver nu API-nyckel | `AuthFilter.java` |
| **L-1** SW hardcoded paths | Dynamisk context path via `self.registration.scope` | `sw-push.js` |
| **L-2** Google Fonts SRI | Accepterad risk: Google Fonts ändrar CSS dynamiskt, SRI ej tillämpbart | Ingen ändring |
| **L-3** MySQL utan SSL | Ändrat `useSSL=false` → `useSSL=true` i emergency-URL:er | `DBUtil.java`, `SmartassicDBUtil.java` |
| **L-4** Audit logging | Ny `AuditUtil` + `audit_log`-tabell, loggar login/admin/token/sync/grupp/modul-events | `AuditUtil.java` (ny), `031_audit_log.sql` (ny), `LoginServlet.java`, `AdminApiServlet.java`, `ApiTokenServlet.java`, `SyncConfigServlet.java` |
| **L-5** Deprecated clipboard | `navigator.clipboard.writeText()` med `execCommand`-fallback | `settings.jsp` |

### Nya filer skapade
| Fil | Syfte |
|-----|-------|
| `CryptoUtil.java` | AES-256-GCM kryptering/dekryptering med auto-genererad master key |
| `AuditUtil.java` | Strukturerad audit logging till DB + SLF4J |
| `SecretsConfig.java` | Centraliserad inläsning av hemligheter från `app-secrets.properties` |
| `AppStartupListener.java` | ServletContextListener som laddar SecretsConfig vid startup |
| `app-secrets.properties` | Alla hemligheter i en fil (ej committad till git) |
| `app-secrets.properties.template` | Mall utan lösenord (committad) |
| `030_crypto_config.sql` | Migration: crypto.masterKey i app_config |
| `031_audit_log.sql` | Migration: audit_log-tabell + index |

### Kvarstående öppna fynd (4 st)
| Fynd | Allvarlighet | Kommentar |
|------|-------------|-----------|
| **K-4** SAML strict mode | KRITISK | Konfigurationsändring klar (`SamlConfigUtil.java`), men kräver att IdP (Microsoft Entra ID) matchar — testa SSO-flödet efter deploy |
| **K-1** Full RBAC | HÖG | Designbeslut behövs — se RBAC-analys nedan |
| **L-2** Google Fonts SRI | LÅG | Accepterad risk |
| **M-1** Rate limit kluster | MEDEL | Accepterat: single-server deployment |
