# InfoCaption Dashboard — Installationspaket

## Innehåll

- `icDashBoard.war` — Kompilerad webapp (WAR-fil), redo att deployas till Tomcat
- `install.sql` — Databas-setup (tabeller, grupper, moduler, widgets, config)
- `install.ps1` — Automatiserat installationsscript (valfritt)

## Installation

### 1. MySQL-databas

```sql
CREATE DATABASE icdashboard CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'icdashboarduser'@'localhost' IDENTIFIED BY 'ditt-lösenord-här';
GRANT ALL PRIVILEGES ON icdashboard.* TO 'icdashboarduser'@'localhost';
FLUSH PRIVILEGES;
```

Kör install-scriptet:

```bash
mysql -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < install.sql
```

### 2. Deploy till Tomcat

Kopiera `icDashBoard.war` till Tomcats `webapps/`-mapp:

```powershell
copy icDashBoard.war C:\Tomcat9\webapps\
```

Starta Tomcat. WAR-filen packas upp automatiskt till `webapps/icDashBoard/`.

### 3. Första användaren

1. Öppna `http://localhost:8080/icDashBoard/`
2. Registrera ett konto (registreringsfliken visas som standard)
3. Gör dig själv till admin:

```sql
UPDATE users SET is_admin = 1 WHERE id = 1;
```

### 4. Konfigurera

Logga in och gå till **Admin > Inställningar**. Uppdatera:

- `db.password` — Ditt MySQL-lösenord
- `acs.*` — Azure Communication Services (för e-postutskick)
- `api.importKey` — API-nyckel för statistikimport
- `db.smartassic.*` — Extern databas (valfritt, för kontaktlistor)

### 5. SAML SSO (valfritt)

Redigera `WEB-INF/saml.properties` med dina Azure Entra ID-värden:
- `sp.entityid` och `sp.assertion_consumer_service.url` — Din domän
- `idp.entityid` och `idp.single_sign_on_service.url` — Ditt Entra tenant-ID
- `idp.x509cert` — Base64-certifikat från Azure

Se `docs/SAML-SETUP.md` för detaljerad guide.

**SSO-specifika inställningar** (Admin → Inställningar):
- `sso.emailClaimUri` m.fl. — Anpassa SAML claim-URI:er till ditt Azure-setup
- `sso.departmentClaimUri` — Claim-URI för avdelning (för automatisk grupptilldelning)
- `sso.autoAssignGroups` — Aktivera automatisk grupptilldelning baserat på avdelning
- `sso.autoCreateGroups` — Skapa grupper automatiskt om ingen matchande grupp finns
- `auth.loginMethods` — Välj inloggningsmetod: `both`, `password` eller `sso`

### 6. Grupper (valfritt)

I **Admin → Grupper** kan du:
- Skapa/redigera/ta bort grupper
- Koppla grupper till SSO-avdelningar (fältet "SSO-avdelning")
- Hantera medlemmar manuellt
- Styr modulsynlighet via grupptilldelning

### 7. HTTPS (rekommenderat)

Konfigurera SSL-connector i Tomcats `server.xml` på port 8443.
Appen har automatisk HTTP→HTTPS redirect konfigurerad i `web.xml`.

## Admin-panelen

Admin-panelen (**Admin** i sidomenyn) har 5 flikar:

1. **Användare** — Hantera användarkonton, admin-status
2. **Grupper** — CRUD för grupper, SSO-koppling, medlemshantering
3. **Datasynk** — Automatisk dataimport från externa API:er
4. **Widgets** — Hantera dashboard-widgets (inbyggda + anpassade)
5. **Inställningar** — Alla konfigureringsnycklar (databas, e-post, SSO, etc.)

## Krav

- Java 21+
- Apache Tomcat 9.0+
- MySQL 5.7+
