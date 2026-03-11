# InfoCaption Dashboard (icDashBoard)

En modulär dashboard-plattform for InfoCaption supportorganisationer. Java Servlet/JSP med MySQL, SAML2 SSO, gruppbaserad åtkomstkontroll, adminsystem och datasync. Moduler laddas som iframes.

## Teknikstack

- **Java 21** + **Apache Tomcat 9.0.100** (Servlet 4.0)
- **MySQL 5.7** (utf8mb4)
- **BCrypt** + **SAML2 SSO** (Microsoft Entra ID)
- **Eclipse IDE** med WTP — ingen Maven/Gradle

## Kom igång

Se **[icDashBoard/docs/GETTING_STARTED.md](icDashBoard/docs/GETTING_STARTED.md)** for fullständig installationsguide.

Snabbversion:
1. Installera Java 21, MySQL 5.7, Eclipse med WTP
2. Skapa databas och kör `install-package/install.sql`
3. Kopiera `WEB-INF/app-secrets.properties.template` till `app-secrets.properties`
4. Konfigurera Tomcat i Eclipse, starta och navigera till `/icDashBoard/`

## Dokumentation

| Dokument | Innehåll |
|----------|----------|
| [icDashBoard/README.md](icDashBoard/README.md) | Arkitektur, API-endpoints, moduler, changelog |
| [docs/GETTING_STARTED.md](icDashBoard/docs/GETTING_STARTED.md) | Installationsguide steg-för-steg |
| [docs/API-GUIDE.md](icDashBoard/docs/API-GUIDE.md) | API-dokumentation, Bearer tokens |
| [docs/SAML-SETUP.md](icDashBoard/docs/SAML-SETUP.md) | SSO med Microsoft Entra ID |
| [docs/MODULE-GUIDE.md](icDashBoard/docs/MODULE-GUIDE.md) | Skapa egna moduler |
| [CLAUDE.md](CLAUDE.md) | Kodkonventioner och arkitekturbeslut |

## Projektstruktur

```
claudable/
├── icDashBoard/           ← Huvudprojektet (Java Servlet/JSP)
│   ├── src/main/java/     ← 39 servlets, 7 filters, 3 models, 22 utilities
│   ├── src/main/webapp/   ← 10 JSP-sidor, 16 moduler, 12 JARs
│   ├── sql/               ← 35 migreringsskript
│   └── docs/              ← All dokumentation
├── install-package/       ← Deploybart paket (WAR + SQL + installskript)
├── apache-tomcat-9.0.100/ ← Inbäddad Tomcat (ej committat)
└── CLAUDE.md              ← Kodkonventioner
```
