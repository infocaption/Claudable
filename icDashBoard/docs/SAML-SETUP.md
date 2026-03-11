# SAML2 SSO Setup — Microsoft Entra ID

Steg-för-steg guide för att konfigurera SAML2 Single Sign-On mellan InfoCaption Dashboard och Microsoft Entra ID (Azure AD).

## Förutsättningar

- Admin-åtkomst till Azure Portal (Microsoft Entra ID)
- InfoCaption Dashboard deployad och nåbar (helst HTTPS i produktion)
- Konfigurationsfilen: `WEB-INF/saml.properties`

---

## Steg 1: Registrera Enterprise Application

1. Gå till **Azure Portal** → **Microsoft Entra ID** → **Enterprise applications**
2. Klicka **New application**
3. Klicka **Create your own application**
4. Namn: `InfoCaption Dashboard`
5. Välj: *Integrate any other application you don't find in the gallery (Non-gallery)*
6. Klicka **Create**

## Steg 2: Konfigurera SAML Single Sign-On

1. I din nya Enterprise App, gå till **Single sign-on** i vänstermenyn
2. Välj **SAML** som sign-on method
3. Under **Basic SAML Configuration**, klicka **Edit** och fyll i:

| Fält | Värde |
|------|-------|
| **Identifier (Entity ID)** | `https://DIN-DOMAN/icDashBoard` |
| **Reply URL (ACS URL)** | `https://DIN-DOMAN/icDashBoard/saml/acs` |
| **Sign on URL** | `https://DIN-DOMAN/icDashBoard/saml/login` |

> **Lokalt test**: Byt `DIN-DOMAN` mot `localhost:8080`

4. Klicka **Save**

## Steg 3: Konfigurera Attributes & Claims

1. Under **Attributes & Claims**, klicka **Edit**
2. Se till att **NameID** (Subject) är inställd på:
   - **Name identifier format**: `Email address`
   - **Source attribute**: `user.mail`
3. Lägg till följande claims (om de inte redan finns):

| Claim Name | Namespace | Source attribute | Syfte |
|-----------|-----------|-----------------|-------|
| `emailaddress` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims` | `user.mail` | E-postadress |
| `givenname` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims` | `user.givenname` | Förnamn |
| `surname` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims` | `user.surname` | Efternamn |
| `name` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims` | `user.displayname` | Visningsnamn (fallback 1) |
| `displayname` | `http://schemas.microsoft.com/identity/claims` | `user.displayname` | Visningsnamn (fallback 2) |
| `department` | *(valfritt namespace eller "department/department")* | `user.department` | Avdelning (för automatisk grupptilldelning) |

4. Klicka **Save**

> **Alla claim-URI:er är konfigurerbara** i Admin → Inställningar (config-nycklar: `sso.emailClaimUri`, `sso.givenNameClaimUri`, etc.). Om ditt Azure-setup skickar claims med andra URI:er kan du ändra dem där utan kodändring.

## Steg 4: Hämta IdP-värden till saml.properties

Under **SAML Certificates** och **Set up InfoCaption Dashboard** i Azure Portal hittar du tre värden som behövs:

| Azure Portal-fält | saml.properties-nyckel |
|-------------------|----------------------|
| **Microsoft Entra Identifier** | `idp.entityid` |
| **Login URL** | `idp.single_sign_on_service.url` |
| **Certificate (Base64)** — ladda ner | `idp.x509cert` |

### Certifikatet

1. Ladda ner **Certificate (Base64)** (.cer-fil)
2. Öppna filen i en texteditor
3. Kopiera allt **mellan** `-----BEGIN CERTIFICATE-----` och `-----END CERTIFICATE-----`
4. Klistra in det (på en enda rad, utan radbrytningar) som värde för `idp.x509cert`

### Exempel på ifylld saml.properties

```properties
# Service Provider
sp.entityid=https://dashboard.infocaption.com/icDashBoard
sp.assertion_consumer_service.url=https://dashboard.infocaption.com/icDashBoard/saml/acs
sp.nameidformat=urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress

# Identity Provider
idp.entityid=https://sts.windows.net/a1b2c3d4-e5f6-7890-abcd-ef1234567890/
idp.single_sign_on_service.url=https://login.microsoftonline.com/a1b2c3d4-e5f6-7890-abcd-ef1234567890/saml2
idp.x509cert=MIIDBTCCAe2gAwIBAgIQN33ROaIJ6bJBWDCxtmJEbjANBgk...lång base64-sträng...==

# Säkerhet
security.want_assertions_signed=true
security.want_nameid=true
```

## Steg 5: Tilldela användare

1. I Enterprise App, gå till **Users and groups**
2. Klicka **Add user/group**
3. Lägg till de användare eller grupper som ska ha åtkomst
4. Klicka **Assign**

> **Tips**: Lägg till en grupp (t.ex. "InfoCaption Dashboard Users") istället för individuella användare.

## Steg 6: Testa

1. Starta om Tomcat (för att ladda uppdaterad `saml.properties`)
2. Gå till `https://DIN-DOMAN/icDashBoard/login`
3. Klicka **Logga in med Microsoft**
4. Du bör redirectas till Microsoft-inloggning
5. Efter inloggning → tillbaka till dashboarden
6. Verifiera i databasen att användaren skapats:

```sql
SELECT id, username, email, full_name, created_at
FROM users
WHERE password = 'SSO_NO_PASSWORD';
```

## Steg 7: Verifiera SP Metadata (valfritt)

Navigera till `https://DIN-DOMAN/icDashBoard/saml/metadata` i webbläsaren. Du bör se en XML-fil med SP-konfigurationen. Denna URL kan användas i Azure Portal för automatisk SP-import.

---

## Konfigurera Claim-URI:er

Alla SAML claim-URI:er kan ändras i **Admin → Inställningar** utan kodändring:

| Config-nyckel | Standard | Beskrivning |
|---------------|----------|-------------|
| `sso.emailClaimUri` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress` | E-postadress (fallback: NameID) |
| `sso.givenNameClaimUri` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname` | Förnamn |
| `sso.surnameClaimUri` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname` | Efternamn |
| `sso.nameClaimUri` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name` | Visningsnamn (fallback 1) |
| `sso.displayNameClaimUri` | `http://schemas.microsoft.com/identity/claims/displayname` | Visningsnamn (fallback 2, Microsoft-specifik) |
| `sso.departmentClaimUri` | `department/department` | Avdelning (för grupptilldelning) |

Visningsnamnet byggs i ordning: `givenname` + `surname` → `name` → `displayname` → e-postprefix.

---

## Automatisk grupptilldelning (SSO Department)

Vid varje SSO-inloggning kan användare automatiskt tilldelas grupper baserat på sitt `department`-claim från Azure.

### Aktivera/avaktivera

| Config-nyckel | Standard | Beskrivning |
|---------------|----------|-------------|
| `sso.autoAssignGroups` | `true` | Aktivera/avaktivera department-baserad grupptilldelning |
| `sso.autoCreateGroups` | `true` | Skapa grupper automatiskt om ingen matchande grupp finns |

### Hur det fungerar

1. Vid SSO-inloggning läser `SamlAcsServlet` department-claimet från SAML-assertionen
2. Om värdet är pipe-separerat (t.ex. `Utveckling|Kundvård`) hanteras varje avdelning separat
3. För varje avdelning:
   - **Steg 1**: Söker efter grupp med matchande `sso_department`-kolumn
   - **Steg 2**: Om ingen match, söker efter grupp med matchande `name` och länkar den (sätter `sso_department`)
   - **Steg 3**: Om fortfarande ingen match och `sso.autoCreateGroups=true`, skapas en ny grupp
4. Gamla SSO-gruppmedlemskap rensas — användaren synkas till aktuella avdelningar

### Koppla befintliga grupper till SSO-avdelningar

I **Admin → Grupper**, redigera en grupp och fyll i fältet **"SSO-avdelning (department)"**. Värdet måste matcha exakt det som Azure skickar i department-claimet.

> **Tips**: Kontrollera Tomcat-loggen efter SSO-inloggning — `SAML ACS: department claim value = [värde]` visar vad Azure skickar.

### Pipe-separerade avdelningar

Azure kan skicka flera avdelningar i ett enda claim-värde separerat med `|`:

```
Utveckling|Kundvård
```

Varje del behandlas som en separat avdelning och användaren tilldelas alla matchande grupper.

---

## Inloggningsmetoder

Konfigurera vilka inloggningsmetoder som visas via `auth.loginMethods` i Admin → Inställningar:

| Värde | Beteende |
|-------|----------|
| `both` | Visar både lösenordsformulär och SSO-knapp (standard) |
| `password` | Bara lösenordsinloggning |
| `sso` | Bara SAML2 SSO — auto-redirect till Entra ID |

---

## Felsökning

| Problem | Lösning |
|---------|---------|
| "SSO-inloggning misslyckades" | Kontrollera Tomcat-loggen för SAML-felmeddelanden |
| Certifikatfel | Se till att certifikatet inte har radbrytningar i `saml.properties` |
| "AADSTS50011: Reply URL does not match" | ACS URL i Azure Portal måste matcha exakt med `sp.assertion_consumer_service.url` |
| Användare skapas inte | Kontrollera att MySQL är igång och att email-adressen inte redan finns med annat username |
| NameID saknas | Kontrollera att NameID format är "Email address" i Azure Claims |
| Claims mappas inte rätt | Kontrollera claim-URI:er i Admin → Inställningar. Jämför med Azure Portal → Claims |
| Gruppsync: "Duplicate entry" | Gruppen finns redan med samma namn. `SamlAcsServlet` försöker länka automatiskt, men om det misslyckas: sätt `sso_department` manuellt i Admin → Grupper |
| Avdelning hittas inte | Logga in och kontrollera Tomcat-loggen: `SAML ACS: department claim value = ...`. Om `null`, kontrollera `sso.departmentClaimUri` och Azure claims |
| Användare hamnar i fel grupper | Kontrollera att `sso_department` på gruppen matchar exakt vad Azure skickar |

## Säkerhet

- **HTTPS**: Obligatoriskt i produktion. SAML-assertions innehåller känslig identitetsdata.
- **Certifikatrotation**: Azure roterar certifikat periodiskt. Uppdatera `idp.x509cert` vid rotation.
- **SSO-användare**: Får `password = 'SSO_NO_PASSWORD'` i databasen. De kan inte logga in med lösenord.
- **Session fixation**: Gammal session invalideras före ny skapas vid SSO-inloggning.
- **CSRF**: CSRF-token genereras vid session-skapande, skyddar alla state-changing requests.
