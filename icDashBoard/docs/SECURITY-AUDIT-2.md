# Säkerhetsanalys v2 — InfoCaption Dashboard (icDashBoard)

**Datum**: 2026-02-16
**Granskare**: Claude Opus 4.6 (automatiserad analys)
**Omfattning**: All Java-kod (servlets, filters, utilities, models), alla JSP-sidor, JavaScript, HTML-moduler, web.xml, properties-filer. Exkluderat: PowerShell-scripts, Tomcat-konfiguration.
**Metod**: Statisk kodanalys av 5 parallella specialiserade agenter (autentisering, injection, XSS/CSRF, kryptografi, filuppladdning/SSRF).

---

## Sammanfattning

| Allvarlighetsgrad | Antal |
|-------------------|-------|
| KRITISK           | 4     |
| HÖG               | 9     |
| MEDEL             | 18    |
| LÅG               | 14    |
| INFO              | 0     |
| **Totalt**        | **45** |

### Positiva observationer (korrekt implementerat)

- Session fixation-skydd på båda login-vägar (password + SAML)
- BCrypt med work factor 12 för lösenordshashing
- CSRF-token genereras med `SecureRandom` (256 bitar) + constant-time jämförelse
- Alla SQL-queries använder `PreparedStatement` med parametrar — ingen SQL-injection hittad
- Ingen `Runtime.exec()` eller `ProcessBuilder` — ingen command injection möjlig
- ZIP-upload har path traversal-skydd, filtyp-whitelist, zipbomb-skydd (500 filer / 100MB)
- postMessage origin-validering i alla JSP-lyssnare
- Custom error pages förhindrar stack trace-läckage
- Ingen känslig data i loggar
- Comprehensive SSRF IP-validering i SyncExecutor
- SyncExecutor validerar tabellnamn mot whitelist + kolumner mot INFORMATION_SCHEMA
- Custom widgets korrekt sandboxade (`sandbox='allow-scripts'` utan `allow-same-origin`)

---

## KRITISK

### K1. Modul-iframe saknar sandbox — full åtkomst till parent-sidan

**Fil**: `src/main/webapp/dashboard.jsp:1435`

Modul-iframen deklareras utan `sandbox`-attribut:
```html
<iframe class="module-frame" id="moduleFrame" style="display: none;"></iframe>
```

Eftersom moduler laddas från samma origin (`/icDashBoard/modules/...`) har JavaScript i modulen obegränsad åtkomst till:
- `window.parent.document` (hela dashboard-DOM:en)
- Sessionscookies och sessionStorage
- CSRF-token i parent-sidans meta-tag
- Alla API-anrop som den inloggade användaren

**Attack**: Vilken autentiserad användare som helst kan ladda upp en delad modul med skadlig JavaScript. När en annan användare (inklusive admin) öppnar modulen exekveras koden med offrets fulla sessionsbehörigheter — session hijacking, privilege escalation, data exfiltration.

**Kontrast**: Custom widgets (rad 1850) är korrekt sandboxade med `sandbox='allow-scripts'`.

**Rekommendation**: Servera modulinnehåll från en separat origin (t.ex. `modules.icdashboard.infocaption.com`) eller lägg till `sandbox="allow-scripts"` (utan `allow-same-origin`). Kräver omdesign av postMessage-baserad CSRF-tokenutbyte.

---

### K2. SAML strict mode avaktiverat + signaturer ej krävda

**Fil**: `src/main/java/.../util/SamlConfigUtil.java:63-66`

```java
samlData.put("onelogin.saml2.security.authnrequest_signed", false);
samlData.put("onelogin.saml2.security.want_messages_signed", false);
samlData.put("onelogin.saml2.security.want_assertions_encrypted", false);
samlData.put("onelogin.saml2.strict", false);
```

Kommentaren säger "Relaxed settings for development" men detta körs i produktion.

- **`strict = false`**: Hoppar över destination-validering, audience restriction, tidsstämpelkontroller (NotBefore/NotOnOrAfter)
- **`want_messages_signed = false`**: Yttre SAML-responsen behöver ej vara signerad → möjliggör XML Signature Wrapping (XSW)-attacker
- **`want_assertions_encrypted = false`**: SAML-attribut (namn, email, avdelning) synliga i Base64-form i webbläsaren

**Rekommendation**: Sätt `strict = true` och `want_messages_signed = true`. Generera SP-signeringsnyckel och aktivera `authnrequest_signed`.

---

### K3. Hårdkodad Azure ACS-åtkomstnyckel i källkod

**Fil**: `src/main/java/.../util/AcsEmailUtil.java:33`

```java
private static final String FALLBACK_ACCESS_KEY = "7GfY0fOJ2au...";
```

88-tecken Base64-nyckel som ger full åtkomst till Azure Communication Services-kontot. Synlig i källkod, Git-historik, WAR-filer. Används som fallback via `AppConfig.get("acs.accessKey", FALLBACK_ACCESS_KEY)` vid DB-avbrott.

**Rekommendation**: Ersätt fallback med `"CHANGE_ME"`. Rotera nyckeln omedelbart.

---

### K4. Databasuppgifter i db.properties (Git-spårad)

**Filer**: `src/main/webapp/WEB-INF/db.properties:11-17`, `DBUtil.java:31-33`, `SmartassicDBUtil.java:28-34`

```properties
db.password=REDACTED
db.smartassic.password=REDACTED
```

Filen visas som untracked (`??`) i `git status` — risk att den committas. Innehåller lösenord i klartext.

**Rekommendation**: Lägg till `db.properties` i `.gitignore`. Skapa `db.properties.example` med platshållare. Rotera alla exponerade lösenord.

---

## HÖG

### H1. Hårdkodad API-nyckel i AuthFilter ignorerar AppConfig

**Fil**: `src/main/java/.../filter/AuthFilter.java:25`

```java
if (apiKey != null && apiKey.equals("REDACTED")) {
```

Tre problem:
1. AppConfig-nyckeln `api.importKey` ignoreras — nyckelrotation via Admin-panelen har noll effekt
2. `String.equals()` är sårbar för timing-sidokanalsattacker (CsrfFilter använder korrekt `MessageDigest.isEqual()`)
3. Nyckeln är trivialt gissningsbar

**Rekommendation**: Använd `AppConfig.get("api.importKey", FALLBACK)` + `MessageDigest.isEqual()`. Ersätt med kryptografiskt slumpmässig nyckel (32+ bytes).

---

### H2. AuthFilter använder allowlist istället för deny-by-default

**Filer**: `WEB-INF/web.xml:25-72`, `AuthFilter.java:22-28`

AuthFilter mappas till specifika URL-mönster individuellt (12 st `<filter-mapping>`). Nya JSP-sidor, servlets eller statiska resurser som läggs till utan motsvarande mapping är oskyddade. Dessutom använder import-endpointkontrollen `req.getRequestURI()` med `startsWith()` — opererar på rå (ej avkodad) URI, potentiellt kringgåbar via URL-encoding.

**Rekommendation**: Ändra till wildcard `/*` med explicit exkluderingslista för publika sökvägar.

---

### H3. Ingen per-konto lockout + X-Forwarded-For spoofbar

**Filer**: `LoginServlet.java:39-98`, `RateLimitFilter.java:87-94`

RateLimitFilter begränsar till 10 POST/60s per IP, men:
1. Ingen per-konto lockout — ett distribuerat botnet kan gissa obegränsat mot ett konto
2. `X-Forwarded-For` litas på okritiskt — utan reverse proxy kan angripare spoofa headern och kringgå all rate limiting

**Rekommendation**: Implementera per-konto lockout (lås 15 min efter 5 misslyckade försök). Lita bara på `X-Forwarded-For` bakom känd reverse proxy.

---

### H4. Öppen registrering utan verifiering

**Fil**: `RegisterServlet.java:29-104`

Vem som helst kan skapa konto utan e-postverifiering, CAPTCHA, inbjudningskod eller domänrestriktion. Konton är omedelbart aktiva.

**Rekommendation**: Lägg till e-postverifiering. Lägg till CAPTCHA. Överväg domänrestriktion eller admin-skapade konton.

---

### H5. Stored XSS — Oescapad användardata i manage-users.jsp

**Fil**: `src/main/webapp/manage-users.jsp:310-325`

7 instanser av användardata (fullName, email, username, profilePictureUrl) renderas utan `Esc.h()`:

- Rad 310-312: `data-name`, `data-email`, `data-username` attribut — `"` breakout möjligt
- Rad 316: `<img src="<%= profilePic %>">` — src-attribut utan escaping
- Rad 322-325: `.mod-name`, `.mod-desc`, `.mod-username` — rå HTML-content

SSO-användare med manipulerat display name kan trigga XSS för alla som besöker sidan.

**Rekommendation**: Wrappa alla `<%= ... %>` med `Esc.h()`.

---

### H6. Stored XSS — Oescapad användardata i dashboard.jsp

**Fil**: `src/main/webapp/dashboard.jsp:~1365-1380`

5 instanser av user profile data (fullName, email, profilePictureUrl) utan HTML escaping i topbar, user dropdown, avatar-sektion.

**Rekommendation**: Applicera `Esc.h()` på alla `<%= user.getXxx() %>`.

---

### H7. SAML-properties med tenant ID + IdP-cert i källkod

**Fil**: `WEB-INF/saml.properties:15-18`

Innehåller Azure Entra ID tenant ID, fullständigt IdP X.509-signeringscertifikat, SSO-endpoint-URL:er. Ger angripare all info för riktade attacker mot SSO-integrationen.

**Rekommendation**: Lägg till `saml.properties` i `.gitignore`. Skapa template med platshållare.

---

### H8. Ingen SSL/TLS för extern databasanslutning

**Filer**: `WEB-INF/db.properties:15`, `SmartassicDBUtil.java:28`

```
jdbc:mysql://10.201.21.10:3306/smartassic?useSSL=false&allowPublicKeyRetrieval=true
```

Credentials och all querydata (kontaktuppgifter, företagsnamn, persondata) skickas okrypterat. `allowPublicKeyRetrieval=true` möjliggör MITM-attack.

**Rekommendation**: Sätt `useSSL=true&requireSSL=true&verifyServerCertificate=true`. Ta bort `allowPublicKeyRetrieval=true`.

---

### H9. Stored XSS via custom widget HTML/JS (admin → alla användare)

**Filer**: `AdminApiServlet.java:708-783`, `WidgetApiServlet.java:120-121`

Custom widgets accepterar godtycklig `customHtml`/`customJs` utan sanitering. Widgets renderas dock i `sandbox='allow-scripts'` iframe (utan `allow-same-origin`), vilket begränsar åtkomst till parent. Dock har sandboxen ingen CSP — nätverksåtkomst (fetch till externa servrar) är obegränsad.

**Rekommendation**: Injicera CSP meta-tag i srcdoc: `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline';">`.

---

## MEDEL

### M1. JavaScript single-quote injection i admin.jsp `esc()`-funktion

**Fil**: `admin.jsp:1320`

Client-side `esc()` escapar `&`, `<`, `>`, `"` men **inte** enkelfnutt (`'`). Används i onclick-attribut med enkelfnutts-strings. `O'Brien` eller `');alert(document.cookie);//` bryter ut ur strängen. 5 anropspunkter: deleteUser, deleteGroup, removeGroupMember, deleteSync, deleteWidget.

**Rekommendation**: Lägg till `.replace(/'/g, '&#x27;')` i `esc()`.

---

### M2. DOM XSS — Oescapad `e.message` i innerHTML (admin.jsp)

**Fil**: `admin.jsp:623,720,939,1142,1305,1340,1470`

7 catch-block infogar `e.message` direkt i innerHTML utan `esc()`. `showAlert()` helper använder korrekt `esc(message)` men dessa platser kringgår den.

**Rekommendation**: Wrappa `e.message` med `esc()` överallt.

---

### M3. Stored XSS i settings.jsp

**Fil**: `settings.jsp:271-300`

5 instanser av användardata i HTML-attribut (value="...") och textinnehåll utan escaping. Self-XSS men kan kombineras med andra sårbarheter.

**Rekommendation**: Applicera `Esc.h()`.

---

### M4. SSRF-bypass via DNS rebinding i SyncExecutor

**Fil**: `SyncExecutor.java:59, 147-153`

TOCTOU-gap: DNS-upplösning vid validering är separat från DNS-upplösning vid HTTP-anropet. Short TTL → public IP vid check → private IP vid fetch. Kräver admin-åtkomst + kontrollerad DNS.

**Rekommendation**: Bind upplöst IP och rutta HTTP-anropet genom den specifika adressen.

---

### M5. HTTP-redirect kringgår SSRF-kontroller i SyncExecutor

**Fil**: `SyncExecutor.java:31`

`followRedirects(HttpClient.Redirect.NORMAL)` — extern server kan redirecta till intern adress utan re-validering.

**Rekommendation**: Sätt `NEVER` och hantera redirects manuellt med re-validering.

---

### M6. Health check-URL:er från databas möjliggör blind SSRF

**Fil**: `ServerListApiServlet.java:161-182`

Health checks gör HTTP GET till URL:er från `servers`-tabellen + följer redirects. Om angripare kan styra en server-URL (via import API:t med svag nyckel) kan interna tjänster probes. Responskroppen kasseras — bara statuskod returneras.

**Rekommendation**: Applicera `validateUrlNotInternal()` på health check-URL:er. Inaktivera redirect-following.

---

### M7. BodySizeLimitFilter kringgås via chunked transfer encoding

**Fil**: `BodySizeLimitFilter.java:43-46`

Kontrollerar `getContentLengthLong()` som returnerar `-1` vid chunked encoding → oändligt stora bodys tillåts.

**Rekommendation**: Wrappa InputStream i en counting stream med bytelimit.

---

### M8. Obegränsad request body-storlek i servlets

**Filer**: `AdminApiServlet.java`, `EmailApiServlet.java:690-698`, `SyncConfigServlet.java:528-534`, `GroupApiServlet.java:143-149`, `ContactListServlet.java:376-385`, `CustomerStatsApiServlet.java:526-532`

Alla `readRequestBody()`-metoder läser hela body i `StringBuilder` utan storleksbegränsning. Multi-GB POST → OutOfMemoryError.

**Rekommendation**: Begränsa läsning till rimlig maxstorlek (t.ex. 1 MB) i varje readRequestBody().

---

### M9. Svag lösenordspolicy

**Fil**: `RegisterServlet.java:60`

Enda krav: minimum 6 tecken. Inga krav på versaler, siffror, specialtecken.

**Rekommendation**: Öka till 8 tecken (NIST 800-63B). Kontrollera mot vanliga lösenordslistor.

---

### M10. Användarnamn/email-enumering via registreringsfel

**Fil**: `RegisterServlet.java:90-97`

Separata felmeddelanden för duplicerat användarnamn vs. email gör det möjligt att bekräfta konton.

**Rekommendation**: Använd ett generiskt meddelande.

---

### M11. Logout rensar inte cookie + GET-only + CSRF-exkluderad

**Fil**: `LogoutServlet.java:14-21`

`session.invalidate()` anropas men JSESSIONID-cookien rensas inte explicit. GET-baserad logout möjliggör CSRF-framtvingad utloggning via `<img src="/icDashBoard/logout">`.

**Rekommendation**: Rensa JSESSIONID med `maxAge=0`. Gör logout till POST med CSRF-token.

---

### M12. SSO-användare password login → unhandled exception

**Filer**: `LoginServlet.java:59`, `PasswordUtil.java:11-13`

SSO-användare har `password = 'SSO_NO_PASSWORD'` (ej BCrypt-hash). `BCrypt.checkpw()` kastar `IllegalArgumentException`. LoginServlet fångar bara `SQLException` → 500-fel.

**Rekommendation**: Lägg till formatkontroll i `PasswordUtil.verify()` — returnera `false` om hash inte börjar med `$2`.

---

### M13. Admin-status ej omvaliderad från DB under session

**Fil**: `AdminUtil.java:20-37`

`user.isAdmin()` läses från session (satt vid login). Om admin-status tas bort medan användaren är inloggad behåller sessionen admin-behörighet i upp till 30 min.

**Rekommendation**: Re-validera admin-flaggan från DB vid känsliga operationer.

---

### M14. Session cookie saknar Secure/SameSite/tracking-mode

**Fil**: `WEB-INF/web.xml:418-420`

Ingen `<cookie-config>` konfigurerad. Tomcat 9 defaultar `HttpOnly=true` men `Secure` sätts ej explicit.

**Rekommendation**: Lägg till:
```xml
<cookie-config>
    <http-only>true</http-only>
    <secure>true</secure>
</cookie-config>
<tracking-mode>COOKIE</tracking-mode>
```

---

### M15. SAML skapar användare utan domänrestriktion

**Fil**: `SamlAcsServlet.java:192-213`

Auto-skapar konton för alla SSO-användare utan domänkontroll. Om Azure-tenanten har gästanvändare får alla ett dashboardkonto.

**Rekommendation**: Konfigurerbar domän-allowlist (`sso.allowedDomains`).

---

### M16. SAML department claims kan skapa/modifiera grupper

**Fil**: `SamlAcsServlet.java:270-309`

`sso.autoCreateGroups = true` (default) → valfritt department-claimvärde skapar ny grupp. Matchning mot befintligt gruppnamn sätter automatiskt `sso_department`.

**Rekommendation**: Default `sso.autoCreateGroups` till `false`. Kräv admin-åtgärd.

---

### M17. Hemligheter lagras i klartext i app_config

**Fil**: `AppConfig.java` (hela config-systemet)

`is_secret`-flaggan styr bara UI-maskering. Faktiska DB-värden är okrypterade. DB-åtkomst exponerar alla hemligheter.

**Rekommendation**: Implementera applikationsnivå-kryptering (AES-256-GCM) för hemliga värden.

---

### M18. Synk-credentials returneras i klartext

**Fil**: `SyncConfigServlet.java:510`

`auth_config` (API-nycklar, bearer tokens, lösenord) returneras verbatim i JSON till admin-klienten. Synligt i DevTools, minne, HTTP-proxies.

**Rekommendation**: Maskera känsliga fält innan de returneras.

---

## LÅG

### L1. CSP `unsafe-inline` neutraliserar XSS-skydd

**Fil**: `SecurityHeaderFilter.java:28-29`

`script-src 'self' 'unsafe-inline'` gör att CSP inte skyddar mot XSS. Nödvändigt pga inline-scripts i JSP:er.

**Rekommendation**: Långsiktigt: migrera till nonce-baserad CSP.

---

### L2. Email body accepterar godtycklig HTML

**Fil**: `EmailApiServlet.java:347-533`

`bodyHtml` accepterar rå HTML. Template-variabler escapas korrekt, men base-body tillåter tracking pixels, phishing-formulär, CSS exfiltration.

**Rekommendation**: Begränsa mailskapande till admin eller implementera HTML-sanitizer.

---

### L3. Module nav renderar data utan client-side escaping

**Fil**: `dashboard.jsp:2254-2274`

`renderModuleNav()` och `renderModuleCards()` insertar `module.name`, `module.icon` direkt i innerHTML utan att använda befintlig `escapeHtml()`.

**Rekommendation**: Applicera `escapeHtml()` på alla moduldata i innerHTML.

---

### L4. Avatarfiler publikt tillgängliga utan autentisering

**Filer**: `AvatarUploadServlet.java:86-100`, `WEB-INF/web.xml`

Avatarfiler lagras under `{webapp-root}/uploads/avatars/avatar_{userId}.{ext}`. AuthFilter har ingen mapping för `/uploads/*`. Sekventiella user-ID:n gör enumering trivial.

**Rekommendation**: Lägg till AuthFilter-mapping för `/uploads/*`.

---

### L5. Moduldeletion validerar inte directory_name mot path traversal

**Fil**: `ModuleManageServlet.java:264-268`

`deleteDirectory()` anropas med `new File(modulesRoot, module.getDirectoryName())` utan att verifiera att resolved path är inom modules-rooten.

**Rekommendation**: Normalisera sökvägen och verifiera att den börjar med modules-root.

---

### L6. LIKE-wildcards ej escapade i domänfilter

**Fil**: `ContactListServlet.java:230-234`

`%` och `_` i user input escapas inte i LIKE-mönster. Parametriserad query förhindrar SQL injection, men bredare matchning möjlig.

**Rekommendation**: Escapa `%` → `\%` och `_` → `\_`.

---

### L7. Inkonsekvent `Pattern.quote()` i JSON-parsing

**Filer**: `SyncConfigServlet.java:539-578`, `CustomerStatsApiServlet.java:700-710`, `AdminApiServlet.java:820-825`

Flera servlets konkatenerar JSON-nycklar direkt i regex utan `Pattern.quote()`, medan andra gör det korrekt. Alla anropare använder idag hårdkodade strängar — säkert, men fragilt.

**Rekommendation**: Använd `Pattern.quote()` konsekvent.

---

### L8. Ingen email-validering vid registrering

**Fil**: `RegisterServlet.java:29-105`

Ingen email-formatvalidering. Valfri sträng accepteras. Förorenar databasen.

**Rekommendation**: Lägg till email-formatvalidering (regex eller `javax.mail`).

---

### L9. `extractJsonArray` hanterar inte nästlat innehåll

**Filer**: `EmailApiServlet.java:726-738`, `ContactListServlet.java:440-466`

Regex `\\[([^\\]]*)\\]` hanterar inte `]` inuti strängvärden eller nästlade arrayer. Kan orsaka tyst datförlust.

**Rekommendation**: Dokumentera begränsningen eller implementera djupar-medveten parsing.

---

### L10. `extractJsonObject` spårar inte strängkontext

**Fil**: `SyncConfigServlet.java:556-572`

Klammerdjup-räkning hanterar inte `}` inuti JSON-strängar. T.ex. bearer token med `}` orsakar prematur trunkering.

**Rekommendation**: Lägg till strängkontext-spårning i parsern.

---

### L11. PII loggas vid INFO-nivå vid SSO-login

**Fil**: `SamlAcsServlet.java:116`

NameID, email, displayName loggas vid varje SSO-inloggning.

**Rekommendation**: Logga på DEBUG-nivå eller maskera email.

---

### L12. Ingen session-begränsning per användare

**Filer**: `LoginServlet.java`, `SamlAcsServlet.java`

Obegränsat antal simultana sessioner per konto. Komprometterade credentials kan användas parallellt utan detektion.

**Rekommendation**: Invalidera tidigare sessioner vid ny inloggning.

---

### L13. RateLimitFilter fixed-window tillåter boundary burst

**Fil**: `RateLimitFilter.java:61-69`

Fixed window → 2x burst vid fönstergräns (10+10 requests i snabb följd).

**Rekommendation**: Sliding window eller token bucket-algoritm.

---

### L14. LIMIT-värden konkateneras istället för parametriseras

**Filer**: `EmailApiServlet.java:542`, `ContactListServlet.java:264`

LIMIT-värden via string append. Kommer från `AppConfig.getInt()` (säker int-parsning), men fragilt mönster.

**Rekommendation**: Använd parameteriserade PreparedStatement för LIMIT.

---

## Åtgärdsprioritet

### Omedelbart (denna vecka)

| # | Fynd | Åtgärd |
|---|------|--------|
| K1 | Modul-iframe utan sandbox | Lägg till sandbox eller separat origin |
| K2 | SAML strict mode avaktiverat | Sätt `strict=true`, `want_messages_signed=true` |
| K3 | Hårdkodad ACS-nyckel | Ersätt fallback, rotera nyckel |
| K4 | DB-creds i properties | `.gitignore` + rotera lösenord |
| H1 | Hårdkodad API-nyckel | Använd AppConfig + constant-time compare |
| H5-H6 | Stored XSS i JSP:er | Applicera `Esc.h()` |

### Kort sikt (denna sprint)

| # | Fynd | Åtgärd |
|---|------|--------|
| H2 | AuthFilter allowlist | Ändra till `/*` med exkluderingar |
| H3 | Brute force | Per-konto lockout + XFF-fix |
| H4 | Öppen registrering | Email-verifiering + CAPTCHA |
| H7 | SAML properties i Git | `.gitignore` + template |
| H8 | SSL för extern DB | `useSSL=true` |
| M1-M2 | admin.jsp esc() + innerHTML | Fixa escaping |
| M14 | Session cookie flags | Lägg till `<cookie-config>` |

### Medellång sikt (nästa sprint)

| # | Fynd | Åtgärd |
|---|------|--------|
| M4-M6 | SSRF (DNS rebinding, redirect, health) | IP-pinning, disable redirects |
| M7-M8 | Body size limits | Chunked-aware filter + servlet limits |
| M12 | SSO password login crash | PasswordUtil formatkontroll |
| M17 | Klartext hemligheter i DB | AES-256-GCM kryptering |
| M18 | Synk-creds i klartext | Maskering i API-svar |
| H9 | Widget network CSP | CSP meta-tag i srcdoc |

### Backlog

Alla LÅG-fynd (L1-L14), M9 (lösenordspolicy), M10 (enumering), M11 (logout), M13 (admin re-validering), M15-M16 (SAML domain/grupp).
