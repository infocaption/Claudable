# InfoCaption Dashboard (Java JSP)

En modulär dashboard-plattform för InfoCaption med databasdriven modulhantering, uppladdning och delning.

> **Konverterad från:** `C:\pshellws\icdashboard` (statisk HTML/JS)
> **Konverterad till:** Java Servlet/JSP med MySQL-backed autentisering och modulregister

---

## Teknikstack

| Komponent | Version | Detaljer |
|-----------|---------|----------|
| Java | 21 | Eclipse Adoptium JDK 21.0.10 |
| Tomcat | 9.0.100 | Servlet 4.0 spec (med multipart-stöd) |
| MySQL | 5.7 | localhost:3306, schema `icdashboard` |
| IDE | Eclipse | Dynamic Web Project (WTP) |
| Lösenord | BCrypt | jbcrypt-0.4.jar, work factor 12 |
| JDBC | MySQL Connector/J 8.4.0 | utf8mb4 via connectionCollation |

---

## Snabbstart

### Förutsättningar
- Eclipse IDE med WTP (Web Tools Platform)
- Java 21 JDK installerat
- MySQL 5.7 körandes på localhost:3306
- Schema `icdashboard` med användare `icdashboarduser`

### Starta
1. Importera workspace i Eclipse: `File > Switch Workspace > C:\INFOCAPTION_GIT\InfoCaptionZID\claudable`
2. I **Servers**-vyn: starta **apache-tomcat-9.0.100 at localhost**
3. Navigera till `http://localhost:8080/icDashBoard/`
4. Registrera ett konto via "Registrera dig"-länken
5. Logga in och dashboarden visas

---

## Arkitektur

```
icDashBoard/
├── src/main/java/com/infocaption/dashboard/
│   ├── filter/
│   │   ├── AuthFilter.java           # Skyddar dashboard, moduler, API, hantering
│   │   └── EncodingFilter.java       # UTF-8 på alla requests/responses
│   ├── servlet/
│   │   ├── LoginServlet.java         # GET: visa login, POST: autentisera
│   │   ├── RegisterServlet.java      # GET: visa registrering, POST: skapa konto
│   │   ├── LogoutServlet.java        # Invalidera session, redirect till login
│   │   ├── ModuleApiServlet.java     # GET /api/modules → JSON (modulregister)
│   │   ├── ModuleCreateServlet.java  # GET/POST /module/create (multipart upload)
│   │   ├── ModuleManageServlet.java  # GET/POST /module/manage (CRUD)
│   │   └── ModuleSpecServlet.java    # GET /api/module/spec?id=X → markdown
│   ├── model/
│   │   ├── User.java                 # User POJO (Serializable för HttpSession)
│   │   └── Module.java               # Module POJO (matchar modules-tabellen)
│   └── util/
│       ├── DBUtil.java               # JDBC connection helper (DriverManager)
│       └── PasswordUtil.java         # BCrypt hash/verify wrapper
├── src/main/webapp/
│   ├── WEB-INF/
│   │   ├── web.xml                   # Servlet mappings, filter config, multipart
│   │   └── lib/
│   │       ├── mysql-connector-j-8.4.0.jar
│   │       └── jbcrypt-0.4.jar
│   ├── assets/
│   │   └── logga.png                 # InfoCaption logotyp
│   ├── shared/
│   │   ├── ic-styles.css             # Gemensamt designsystem (CSS variabler)
│   │   └── ic-utils.js               # Delade JS-verktyg (formatering, export)
│   ├── login.jsp                     # Inloggningssida
│   ├── register.jsp                  # Registreringssida
│   ├── dashboard.jsp                 # Huvuddashboard (async API-laddning)
│   ├── create-module.jsp             # Skapa/ladda upp ny modul
│   ├── manage-modules.jsp            # Hantera moduler (redigera, dela, ta bort)
│   └── modules/
│       ├── customer-stats/
│       │   ├── displaystats.html     # Kundstatistik (aktiv)
│       │   ├── displaystats.jsp      # Legacy (kan tas bort)
│       │   ├── allstats.json         # Statistikdata
│       │   └── companies.json        # CRM-mappningar
│       ├── sql-builder/
│       │   ├── sql-builder.html      # SQL-frågebyggare (aktiv)
│       │   ├── sql-builder.jsp       # Legacy (kan tas bort)
│       │   └── sql-queries.json      # SQL-mallar
│       ├── toolbox/
│       │   ├── toolbox.html          # 10+ verktyg (aktiv)
│       │   └── toolbox.jsp           # Legacy (kan tas bort)
│       ├── pong/
│       │   ├── pong.html             # Arkadspel (aktiv)
│       │   └── pong.jsp              # Legacy (kan tas bort)
│       └── docs/
│           ├── docs.html             # Inbyggd dokumentation (aktiv)
│           └── docs.jsp              # Legacy (kan tas bort)
└── docs/
    ├── MODULE-SPEC.md                # Teknisk spec för AI/kodgenerering
    └── MODULE-GUIDE.md               # Mänsklig guide för modulutveckling
```

---

## Modulsystem

### Modultyper

| Typ | Beskrivning | Ägare |
|-----|-------------|-------|
| **system** | Förinstallerade moduler | Ingen (NULL) |
| **private** | Synlig bara för skaparen | Användare |
| **shared** | Synlig för alla inloggade | Användare |

### Modulflöde

1. **Skapa modul** (`/module/create`): Ladda upp .html eller .zip, fyll i metadata
2. **Dashboard** (`/dashboard.jsp`): Moduler laddas via `GET /api/modules` (JSON)
3. **Hantera** (`/module/manage`): Redigera, toggla synlighet, exportera AI-spec, ta bort
4. **AI-spec** (`/api/module/spec?id=X`): Exportera modulspecifikation som Markdown

### Modulfiler

Moduler är ren HTML/CSS/JS (inte JSP). Filerna lagras under `webapp/modules/{directory_name}/` och Tomcat servar dem direkt. Metadata lagras i `modules`-tabellen.

---

## Autentiseringsflöde

```
Användare → GET /icDashBoard/
                 ↓
         welcome-file → /login (LoginServlet)
                 ↓
         GET → visa login.jsp
                 ↓
         POST → validera mot MySQL (BCrypt)
                 ↓ (lyckat)
         session.setAttribute("user", User)
                 ↓
         redirect → /dashboard.jsp
                 ↓
         AuthFilter: session har "user"? → OK, visa dashboard
                 ↓ (saknas)
         redirect → /login (eller 401 JSON för API-anrop)
```

### Skyddade resurser (AuthFilter)
- `/dashboard.jsp`
- `/modules/*`
- `/api/*`
- `/module/*`
- `/create-module.jsp`
- `/manage-modules.jsp`

### Publika resurser (ingen filter)
- `/login` och `/login.jsp`
- `/register` och `/register.jsp`
- `/shared/*` (CSS, JS)
- `/assets/*` (bilder)

---

## Databas

### Anslutning
| Parameter | Värde |
|-----------|-------|
| Host | localhost:3306 |
| Schema | icdashboard |
| Användare | icdashboarduser |
| Lösenord | *(se app-secrets.properties)* |
| Charset | utf8mb4 |
| Collation | utf8mb4_unicode_ci |
| Timezone | Europe/Stockholm |

### JDBC URL
```
jdbc:mysql://localhost:3306/icdashboard?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Stockholm&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
```

> **Viktigt:** `connectionCollation=utf8mb4_unicode_ci` krävs för att emojis (4-byte UTF-8) ska sparas korrekt i MySQL 5.7. Utan detta konverteras emojis till `?`.

### Tabell: `users`

```sql
CREATE TABLE users (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(60)  NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    last_login  TIMESTAMP    NULL,
    is_active   TINYINT(1)   DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Tabell: `modules`

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
    updated_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_modules_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_modules_type (module_type),
    INDEX idx_modules_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Servlet-mappningar (web.xml)

| URL | Servlet/Filter | Metod | Beskrivning |
|-----|---------------|-------|-------------|
| `/login` | LoginServlet | GET/POST | Visa login / autentisera |
| `/register` | RegisterServlet | GET/POST | Visa registrering / skapa konto |
| `/logout` | LogoutServlet | GET | Invalidera session |
| `/api/modules` | ModuleApiServlet | GET | JSON-lista av synliga moduler |
| `/module/create` | ModuleCreateServlet | GET/POST | Skapa modul (multipart, 50MB) |
| `/module/manage` | ModuleManageServlet | GET/POST | Hantera moduler (CRUD) |
| `/api/module/spec` | ModuleSpecServlet | GET | AI-spec export som markdown |
| `/dashboard.jsp` | AuthFilter | * | Kräver aktiv session |
| `/modules/*` | AuthFilter | * | Kräver aktiv session |
| `/api/*` | AuthFilter | * | Kräver session (returnerar 401 JSON) |
| `/module/*` | AuthFilter | * | Kräver aktiv session |
| `/*` | EncodingFilter | * | Sätter UTF-8 encoding |

---

## Systemmoduler

Alla moduler laddas i en iframe inuti `dashboard.jsp`. Modulerna är rena HTML-filer.

| Modul | ID | Fil | Kategori |
|-------|----|-----|----------|
| Kundstatistik | `customer-stats` | `displaystats.html` | analytics |
| SQL Builder | `sql-builder` | `sql-builder.html` | tools |
| Verktygslåda | `toolbox` | `toolbox.html` | tools |
| Pong | `pong` | `pong.html` | tools |
| Dokumentation | `docs` | `docs.html` | tools |

---

## URL-parametrar

Dashboarden stöder direktlänkning till moduler:

```
/icDashBoard/dashboard.jsp?module=customer-stats
/icDashBoard/dashboard.jsp?module=sql-builder
/icDashBoard/dashboard.jsp?module=toolbox
/icDashBoard/dashboard.jsp?module=pong
/icDashBoard/dashboard.jsp?module=docs
```

---

## Felsökning

| Problem | Lösning |
|---------|---------|
| `ClassNotFoundException: Bootstrap` | Kontrollera Tomcat runtime i Eclipse. Verifiera `bootstrap.jar` och `tomcat-juli.jar` i classpath. |
| Login fungerar inte | Kontrollera att MySQL körs och att `users`-tabellen finns. |
| Moduler laddas inte | Kontrollera att `modules`-tabellen finns och innehåller systemmoduler. Kör API: `GET /api/modules` |
| 401 från API | Sessionen har gått ut. Logga in igen. |
| Modul laddas inte i iframe | Kontrollera att du är inloggad (AuthFilter skyddar `/modules/*`) |
| Svenska tecken visas fel | Kontrollera att EncodingFilter är aktivt |
| Tomcat startar inte | Dubbelkolla JDK 21 på `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot` |
| Uppladdad modul syns inte | Kontrollera att filen är .html eller .zip och att entry_file pekar rätt |
| Emojis visas som `?` | Kontrollera att JDBC URL har `connectionCollation=utf8mb4_unicode_ci`. MySQL 5.7 klient (`mysql.exe`) kräver `--default-character-set=utf8mb4` vid INSERT |
| `FIELD()` SQL-fel | `ORDER BY FIELD(...)` kräver MySQL, fungerar inte i andra databaser |

---

## Changelog

### v3.0.0 (2026-02-06) — Databasdrivet modulsystem
- Modulregister i MySQL istället för hårdkodat JavaScript
- Stöd för system-, privata- och delade moduler
- Moduluppladdning: .html eller .zip (max 50MB)
- Modulhanteringssida: redigera, dela, ta bort
- AI-specifikationstext per modul med Markdown-export
- Dashboard hämtar moduler asynkront via `/api/modules`
- Modulfiler migrerade från .jsp till .html (legacy .jsp finns kvar, kan tas bort)
- Sidebar: "Skapa modul" och "Mina moduler" länkar
- JDBC: `connectionCollation=utf8mb4_unicode_ci` för korrekt emoji-stöd
- AuthFilter: returnerar 401 JSON för `/api/*` istället för redirect

### v2.0.0 (2026-02-06) — Java JSP-konvertering
- Konverterad från statisk HTML till Java Servlet/JSP
- Autentisering: login, registrering, logout med BCrypt
- MySQL-backed användardatabas
- AuthFilter skyddar dashboard och moduler
- UTF-8 EncodingFilter

### v1.2.0 (2025-02-05)
- Ny modul: Pong
- VIPS Intranät-länk
- Dokumentationssektion

### v1.1.0 (2025-02-05)
- Ny modul: Dokumentation
- Uppdaterad design med InfoCaption VIPS-stil

### v1.0.0 (2025-02-05)
- Initial release: Dashboard, Kundstatistik, SQL Builder, Verktygslåda

---

## Framtida funktioner

- [x] ~~Autentisering~~ (implementerat v2.0.0)
- [x] ~~Moduler som laddas dynamiskt från server~~ (implementerat v3.0.0)
- [ ] Mörkt tema
- [ ] Rollbaserad åtkomst (admin, viewer)
- [ ] Lösenordsåterställning
- [ ] Connection pooling (Tomcat DBCP/JNDI)
- [ ] Audit log (vem loggade in när)
- [ ] Modulversionshantering
