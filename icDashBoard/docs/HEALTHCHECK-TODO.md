# HealthCheckScheduler — Att göra

## Bakgrund

`HealthCheckScheduler.java` kör idag legacy-check mot `.version.xml` — **fel**. Den ska istället göra samma sak som icCloudGuard-appen (`C:\icCloudGuard`).

## Hur icCloudGuard fungerar

### Två övervakningslägen

**Legacy** (det som är påslaget nu):
- Hämtar `{url}/custom/MasterTimeStamp.txt` per server
- Format: `#MasterTimeStamp=HH:mm# #LastError=...# #Error=Meddelande:|URL (id)# #Error=...#`
- Encoding: Latin-1 (iso-8859-1), kollar Content-Type charset header
- Server är **röd** om:
  - Det finns `Error`-poster
  - `LastError` är satt (och inga separata Error-poster → LastError visas som felkort)
  - `MasterTimeStamp` är äldre än 5 minuter (stale)
- Server är **grön** om inga fel och timestamp är färsk
- Se `C:\icCloudGuard\Services\CloudMonitorService.cs` för exakt parsning

**API (CloudGuard)** (det nya):
- `GET {baseUrl}/api/cloudguard/active` med Bearer token
- Returnerar JSON-array av incidenter med severity (critical/error/warning/info)
- Server är **röd** om det finns aktiva incidenter
- Se `C:\icCloudGuard\Services\CloudGuardApiService.cs`

### State-övergångar som spelar ljud i appen
- Grön → Röd: alert-ljud
- Röd → Grön: ok-ljud
- Redan röd men fler fel: alert igen

## Vad som behöver ändras

### 1. HealthCheckScheduler.java — Skriv om legacy-check

**Ta bort:** `.version.xml`-logiken (`buildVersionUrl`, `classifySeverity` baserad på HTTP-kod)

**Ersätt med:**
- Hämta `{url}/custom/MasterTimeStamp.txt` för varje aktiv server
- Parsa `#Key=Value#`-format (samma regex som C#: `#(\w+)=([^#]*)#`)
- Latin-1 encoding (Java: `Charset.forName("ISO-8859-1")`, kolla Content-Type header)
- Extrahera: `MasterTimeStamp`, `LastError`, alla `Error`-poster
- Klassificera som röd om:
  - Finns Error-poster
  - LastError är satt
  - MasterTimeStamp > 5 min gammal
  - HTTP-fel / timeout vid hämtning
- Spara MasterTimeStamp + felmeddelanden i `server_health_state` (behöver troligen utöka tabellen med `master_timestamp VARCHAR(10)`, `error_count INT`, `error_details TEXT`)

### 2. server_health_state — Utöka tabellen

Lägg till kolumner:
```sql
ALTER TABLE server_health_state
ADD COLUMN master_timestamp VARCHAR(10) NULL AFTER last_error,
ADD COLUMN error_count INT NOT NULL DEFAULT 0 AFTER master_timestamp,
ADD COLUMN error_details TEXT NULL AFTER error_count;
```

`error_details` = JSON-array av felmeddelanden (för visning i modulen)

### 3. API-endpoint `/api/cloudguard/server-status` — Uppdatera

Returnera de nya fälten (master_timestamp, error_count, error_details) så modulen kan visa dem.

### 4. CloudGuard Monitor-modulen — Uppdatera

Istället för att visa CloudGuard-incidenter som primär vy:
- Visa **serverlista med health status** från `/api/cloudguard/server-status`
- Per server: statusfärg (grön/röd), URL, MasterTimeStamp, felmeddelanden
- Sammanfattningskort: Totalt antal servrar, antal gröna, antal röda
- Klick på en röd server → visa Error-poster (meddelande + URL)
- Behåll incidenter/historik som sekundär flik

### 5. URL-mönster

Legacy-URL byggs som: `{server.url}/custom/MasterTimeStamp.txt`

Exempel:
- `https://smartasstest.infocaption.com/custom/MasterTimeStamp.txt`
- `https://demo.infocaption.com/custom/MasterTimeStamp.txt`

### 6. Admin-inställningar (redan på plats)

- `healthmonitor.checkType` = `legacy` → hämtar MasterTimeStamp.txt
- `healthmonitor.checkType` = `api` → läser från CloudGuard-incidenter (befintligt)
- `healthmonitor.checkType` = `both` → kör båda

## Filer att ändra

| Fil | Ändring |
|-----|---------|
| `HealthCheckScheduler.java` | Skriv om `runLegacyChecks()` — MasterTimeStamp.txt istället för .version.xml |
| `028_health_monitor.sql` (eller ny 029) | Utöka `server_health_state` med nya kolumner |
| `CloudGuardApiServlet.java` | Uppdatera `handleServerStatus()` med nya fält |
| `modules/cloudguard-monitor/index.html` | Serverstatus som primär vy, incidenter som sekundär |

## Referenskod

All parsningslogik finns i C#-appen:
- `C:\icCloudGuard\Services\CloudMonitorService.cs` — legacy parsing
- `C:\icCloudGuard\Services\CloudGuardApiService.cs` — API-mode
- `C:\icCloudGuard\Models\MonitorStatus.cs` — datamodell
- `C:\icCloudGuard\ViewModels\MainViewModel.cs` — state-övergångslogik (rad 337-359)
