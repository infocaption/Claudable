# Skapa moduler i InfoCaption Dashboard

En modul i dashboarden är en **HTML-fil som laddas i en iframe**. Du kan skapa egna moduler genom att beskriva vad du vill ha, låta en AI generera koden, och sedan ladda upp filen.

---

## Snabbguide: Skapa en modul med AI (rekommenderat)

Det enklaste sättet att skapa en ny modul är att använda den inbyggda **AI-promptgeneratorn**. Hela flödet tar ca 5 minuter.

### Steg 1 — Öppna "Skapa modul"

I sidomenyn klickar du **Skapa modul** (under "Hantera").

### Steg 2 — Fyll i grundinfo

- **Modulnamn** — Vad modulen ska heta, t.ex. "Kundrapport"
- **Ikon** — Valfri emoji
- **Kategori** — Verktyg, Analys eller Administration
- **Beskrivning** — En kort mening om vad den gör

### Steg 3 — Skriv en AI-specifikation

I textfältet **"AI-specifikation"** beskriver du vad modulen ska göra. Skriv fritt — tänk på det som en beställning. Ju mer detaljer, desto bättre resultat.

**Exempel:**

```
Jag vill ha en modul som visar en lista över alla servrar med deras
versionsnummer. Tabellen ska gå att sortera och filtrera. Visa en
färgkodad badge baserat på version — grön om senaste, gul om en
version gammal, röd om äldre. Hämta data från servrar-API:et.
Det ska finnas en knapp för att exportera listan som CSV.
```

**Tips för bra specifikationer:**
- Beskriv vad modulen ska visa och göra
- Nämn vilken data som behövs (det finns färdiga API:er — se listan i steg 4)
- Beskriv önskad layout (tabell, kort, tabs, etc.)
- Nämn interaktioner (sortering, filtrering, export, etc.)

### Steg 4 — Generera prompt och klistra in i en AI

1. Klicka **"Generera AI-prompt"** (lila knappen under specifikationen)
2. Prompten kopieras automatiskt till ditt urklipp
3. Öppna en ny chatt med **Claude**, **ChatGPT** eller liknande
4. **Klistra in prompten** (Ctrl+V)
5. AI:n genererar en komplett HTML-fil åt dig

> Prompten innehåller automatiskt all teknisk info som AI:n behöver — designsystem, API-endpoints, JavaScript-verktyg etc. Du behöver inte lägga till något.

### Steg 5 — Spara HTML-filen

Kopiera koden som AI:n genererat och spara den som en `.html`-fil på din dator, t.ex. `index.html`.

> **Tips:** De flesta AI-verktyg har en "Kopiera kod"-knapp. Klistra in i Notepad/VS Code och spara som `.html`.

### Steg 6 — Ladda upp filen

Tillbaka i "Skapa modul"-formuläret:

1. Klicka på **uppladdningsytan** eller dra filen dit
2. Välj din `.html`-fil (eller en `.zip` om modulen har flera filer)
3. Välj **synlighet**: Privat (bara du) eller Delad (för andra)
4. Klicka **"Skapa modul"**

### Klart!

Modulen syns direkt i dashboardens meny. Klicka på den för att öppna den.

---

## Vill du redigera en modul efteråt?

1. Gå till **Mina moduler** (i sidomenyn under "Hantera")
2. Klicka **Redigera** på modulen
3. Uppdatera AI-specifikationen om du vill ändra något
4. Klicka **"Generera AI-prompt"** igen — prompten innehåller nu din uppdaterade spec
5. Ge den nya prompten till AI:n och be om en uppdaterad version
6. Ladda upp den nya HTML-filen

---

## Tillgängliga API:er

Moduler kan hämta data från dessa API-endpoints. AI-promptgeneratorn inkluderar alla automatiskt, men här är en översikt:

| API | Vad den ger |
|-----|-------------|
| Kundstatistik | Aggregerad användarstatistik per server (30d) |
| Servrar | Serverlista med företag, maskinnamn, version |
| Hälsokontroll | Status och svarstid för alla servrar |
| Certifikat | SSL-certifikat med utgångsdatum |
| E-postmallar | Skapa, läsa, uppdatera, ta bort mallar |
| Utskick | Skicka massmail via Azure |
| Kontakter | Söka kontakter i extern databas |
| Grupper | Lista grupper och medlemskap |
| Widgets | Lista aktiva widgets |
| Moduler | Lista alla moduler (filtrerat efter grupp) |

---

## Modultyper och synlighet

| Typ | Vem ser den? |
|-----|-------------|
| **Privat** | Bara du |
| **Delad** | Alla, eller bara utvalda grupper |
| **System** | Inbyggda moduler — gruppfiltrerade |

### Gruppsynlighet (för delade moduler)
- **Ingen grupp vald** = synlig för alla inloggade
- **"Alla"-gruppen vald** = synlig för alla
- **Specifika grupper** = bara medlemmar i de grupperna

---

## Avancerat: Skapa modul manuellt

Om du föredrar att skapa moduler utan webb-formuläret:

### 1. Skapa filer

```
src/main/webapp/modules/min-modul/index.html
```

### 2. Minimal HTML-mall

```html
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Min Modul</title>
    <link rel="stylesheet" href="../../shared/ic-styles.css">
</head>
<body>
    <div class="ic-container">
        <div class="ic-header">
            <h1 class="ic-title">Min Modul</h1>
        </div>

        <div class="ic-card">
            <div class="ic-card-body">
                <p>Innehåll här.</p>
            </div>
        </div>
    </div>

    <script src="../../shared/ic-utils.js"></script>
    <script>
        ICUtils.notifyReady('min-modul');
    </script>
</body>
</html>
```

### 3. Registrera i databasen

```sql
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name)
VALUES (1, 'private', 'Min Modul', '📦', 'Kort beskrivning', 'tools', 'index.html', 'min-modul');
```

> **OBS:** MySQL CLI kräver `--default-character-set=utf8mb4` för emojis.

---

## Tekniska krav (för den som vill veta)

Dessa krav hanteras automatiskt av AI-promptgeneratorn, men bra att känna till:

- Modulen är en `.html`-fil (inte JSP, ingen kompilering)
- Inkludera `../../shared/ic-styles.css` i `<head>`
- Inkludera `../../shared/ic-utils.js` före modulens script
- Anropa `ICUtils.notifyReady('modul-id')` när modulen laddat klart
- All UI-text på svenska
- Använd CSS-variabler för färger (inte hårdkodade värden)
- Responsiv design (320px–1920px)
- API-anrop med relativ sökväg: `../../api/...`

---

## Felsökning

| Problem | Lösning |
|---------|---------|
| Modulen syns inte | Kontrollera att den finns i databasen och att `is_active = 1` |
| Tomma sidan | Kontrollera att HTML-filen har rätt sökvägar (`../../shared/...`) |
| Stilar saknas | `../../shared/ic-styles.css` måste inkluderas |
| API ger 401 | Du är inte inloggad — sessionen har gått ut |
| Emojis visas som `?` | MySQL CLI behöver `--default-character-set=utf8mb4` |
| Uppladdad ZIP fungerar inte | ZIP:en måste innehålla minst en `.html`-fil |
| Synlig för fel personer | Kontrollera gruppsynlighet under "Mina moduler" |
