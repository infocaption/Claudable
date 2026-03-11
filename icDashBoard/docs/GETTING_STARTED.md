# Getting Started — InfoCaption Dashboard

Steg-för-steg guide: från klonat repo till körande app.

---

## Förutsättningar

| Verktyg | Version | Nedladdning |
|---------|---------|-------------|
| Java JDK | 21 (Eclipse Adoptium) | https://adoptium.net/ |
| Eclipse IDE | med WTP (Web Tools Platform) | https://www.eclipse.org/downloads/ |
| MySQL | 5.7 | https://dev.mysql.com/downloads/mysql/5.7.html |
| Git | valfri version | https://git-scm.com/ |

Verifiera Java:
```
java -version
javac -version
```

Verifiera MySQL:
```
"C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" --version
```

---

## 1. Klona repot

```bash
git clone <repo-url>
cd claudable
```

---

## 2. Konfigurera MySQL

Logga in som root och skapa databas + användare:

```sql
-- Logga in som root
"C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" -u root -p

-- Skapa databas
CREATE DATABASE IF NOT EXISTS icdashboard
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Skapa användare
CREATE USER IF NOT EXISTS 'icdashboarduser'@'localhost'
  IDENTIFIED BY '<DITT_LÖSENORD>';

-- Ge rättigheter
GRANT ALL PRIVILEGES ON icdashboard.* TO 'icdashboarduser'@'localhost';
FLUSH PRIVILEGES;
```

Kör baslinjeSQL (alla migrationer samlade):

```bash
"C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < install-package/install.sql
```

> **Viktigt:** Använd alltid `--default-character-set=utf8mb4` för att undvika problem med svenska tecken och emojis.

---

## 3. Konfigurera credentials

```bash
cd icDashBoard/src/main/webapp/WEB-INF/
cp app-secrets.properties.template app-secrets.properties
```

Redigera `app-secrets.properties` med dina värden:

```properties
# Lokal databas
db.url=jdbc:mysql://localhost:3306/icdashboard?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Stockholm&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
db.user=icdashboarduser
db.password=<DITT_LÖSENORD>

# Extern databas (valfritt — lämna CHANGE_ME om ej relevant)
db.smartassic.url=jdbc:mysql://EXTERNAL_HOST:3306/smartassic?useSSL=true&...
db.smartassic.user=CHANGE_ME
db.smartassic.password=CHANGE_ME

# API-nyckel (valfritt — generera med t.ex. openssl rand -hex 32)
api.importKey=CHANGE_ME

# Azure Communication Services (valfritt — behövs för e-postutskick)
acs.endpoint=https://YOUR_ACS_NAME.europe.communication.azure.com
acs.host=YOUR_ACS_NAME.europe.communication.azure.com
acs.accessKey=CHANGE_ME
acs.senderAddress=messenger@infocaption.com
acs.apiVersion=2023-03-31
```

> **Committa aldrig** `app-secrets.properties` — filen är gitignored.

---

## 4. Eclipse IDE-setup

### 4.1 Öppna workspace

`File → Switch Workspace → [sökväg till claudable-mappen]`

### 4.2 Importera projektet

1. `File → Import → General → Existing Projects into Workspace`
2. Välj `claudable/icDashBoard` som root directory
3. Markera projektet och klicka `Finish`

### 4.3 Konfigurera Java 21

1. `Window → Preferences → Java → Installed JREs`
2. Lägg till JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
3. Markera den som default

### 4.4 Konfigurera Tomcat

1. `Window → Preferences → Server → Runtime Environments → Add`
2. Välj **Apache Tomcat v9.0**
3. Peka på: `claudable/apache-tomcat-9.0.100`
4. Välj JDK 21 som JRE
5. I **Servers**-vyn: högerklicka → `New → Server`
6. Välj **Tomcat v9.0 Server** och lägg till `icDashBoard`-projektet

### 4.5 JAR-beroenden

Alla JAR-filer finns redan i `WEB-INF/lib/`. Om Eclipse inte hittar dem:

1. Högerklicka på projektet → `Properties → Java Build Path → Libraries`
2. Kontrollera att alla JARs i `WEB-INF/lib/` är inkluderade
3. Vid problem: högerklicka Server → `Clean` → starta om

---

## 5. Starta appen

1. I **Servers**-vyn: högerklicka Tomcat → **Start** (eller **Debug** för debugging)
2. Öppna webbläsaren: `http://localhost:8080/icDashBoard/`
3. Klicka **Registrera dig** och skapa ett konto
4. Logga in — dashboarden visas med systemmoduler

### Gör dig själv till admin

```sql
"C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" -u icdashboarduser -p icdashboard --default-character-set=utf8mb4

UPDATE users SET is_admin = 1 WHERE email = 'din@email.com';
```

Logga ut och in igen. Admin-panelen syns nu i sidofältet.

---

## 6. SAML SSO (valfritt)

Se [SAML-SETUP.md](SAML-SETUP.md) för integration med Microsoft Entra ID (Azure AD).

Kräver:
- Enterprise App registrerad i Azure
- `saml.properties` konfigurerad i `WEB-INF/`
- Aktiverat via Admin → Inställningar

---

## Projektstruktur — snabböversikt

```
icDashBoard/
├── src/main/java/.../
│   ├── filter/     ← 7 filters (Auth, CSRF, Encoding, SecurityHeader, RateLimit, ...)
│   ├── servlet/    ← 39 servlets (Login, SAML, Admin, Drift, Email, MCP, ...)
│   ├── model/      ← User, Module, Group POJOs
│   └── util/       ← 22 utilities (AppConfig, DBUtil, SyncExecutor, CryptoUtil, ...)
├── src/main/webapp/
│   ├── WEB-INF/    ← web.xml, lib/ (12 JARs), secrets
│   ├── shared/     ← ic-styles.css, ic-utils.js, ic-icons.css
│   ├── modules/    ← 16 systemmoduler (HTML/CSS/JS i iframes)
│   └── *.jsp       ← login, dashboard, admin, hanteringssidor
├── sql/            ← 35 migreringsskript (001–032 + baseline)
└── docs/           ← SAML, API, moduler, säkerhetsaudits
```

---

## Vanliga problem

| Problem | Lösning |
|---------|---------|
| Tomcat startar inte | Kontrollera JDK 21-sökväg och att port 8080 är ledig |
| `ClassNotFoundException` | Högerklicka Server → Clean. Kontrollera JAR i `WEB-INF/lib/` |
| Login fungerar inte | Kontrollera MySQL kör + `users`-tabellen finns |
| Emojis visas som `?` | JDBC URL måste ha `connectionCollation=utf8mb4_unicode_ci` |
| Svenska tecken trasiga | MySQL CLI: `--default-character-set=utf8mb4` |
| JSP-ändringar syns inte | Starta om Tomcat eller högerklicka Server → Clean |
| 401 från API | Sessionen har gått ut (30 min). Logga in igen. |
| Admin-panel syns inte | Kör `UPDATE users SET is_admin = 1 WHERE email = '...'` |

---

## Mer dokumentation

| Dokument | Innehåll |
|----------|----------|
| [API-GUIDE.md](API-GUIDE.md) | API-endpoints, Bearer tokens, exempel |
| [MODULE-GUIDE.md](MODULE-GUIDE.md) | Skapa egna moduler |
| [MODULE-SPEC.md](MODULE-SPEC.md) | Teknisk modulspecifikation |
| [SAML-SETUP.md](SAML-SETUP.md) | SSO med Microsoft Entra ID |
| [KNOWLEDGE-BASE.md](KNOWLEDGE-BASE.md) | Kunskapsbas / MCP-integration |
| `../CLAUDE.md` | Kodkonventioner och arkitekturbeslut |
