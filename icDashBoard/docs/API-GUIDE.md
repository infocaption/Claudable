# API-guide — InfoCaption Dashboard

> Senast uppdaterad: 2026-02-20

## Autentisering

Dashboard-API:et stöder tre autentiseringsmetoder:

### 1. Bearer Token (rekommenderat för scripts/automation)

Personliga API-tokens med konfigurerbar livslängd (standard 60 dagar).

```
Authorization: Bearer icd_<token>
```

**Skapa en token:**
1. En admin aktiverar "API-åtkomst" för din användare (Admin → Användare → 🔑 API toggle)
2. Du genererar en token via API:et (kräver inloggad session):
   ```bash
   curl -X POST https://<DIN-DOMAN>/icDashBoard/api/tokens \
     -H "Cookie: JSESSIONID=<din-session>" \
     -H "X-CSRF-Token: <csrf-token>" \
     -H "Content-Type: application/json" \
     -d '{"name": "Mitt PowerShell-script"}'
   ```
3. Svaret innehåller token **en enda gång** — kopiera och spara den!

**Använda token:**
```bash
curl https://<DIN-DOMAN>/icDashBoard/api/drift/summary \
  -H "Authorization: Bearer icd_AbCdEf..."
```

**PowerShell-exempel:**
```powershell
$token = "icd_AbCdEf1234567890..."
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type"  = "application/json"
}

# GET
$response = Invoke-RestMethod -Uri "https://<DIN-DOMAN>/icDashBoard/api/drift/summary" `
    -Headers $headers -Method GET

# POST
$body = @{ hostname = "SRV01"; os = "Windows Server 2022" } | ConvertTo-Json
Invoke-RestMethod -Uri "https://<DIN-DOMAN>/icDashBoard/api/drift/machines" `
    -Headers $headers -Method POST -Body $body
```

### 2. API-nyckel (legacy, för specifika endpoints)

```
X-API-Key: YOUR_API_KEY
```

Stöds på:
- `POST /api/customer-stats/import`
- `/api/drift/*`

### 3. Session (webbläsare)

Cookie-baserad session (`JSESSIONID`) + CSRF-token. Används automatiskt av dashboard-UI:t.

---

## Token-hantering

### Lista dina tokens

```
GET /api/tokens
```

**Svar:**
```json
[
  {
    "id": 1,
    "tokenPrefix": "icd_AbCd",
    "name": "Mitt script",
    "expiresAt": "2026-04-21 14:30:00",
    "lastUsed": "2026-02-20 09:15:00",
    "createdAt": "2026-02-20 14:30:00",
    "status": "active"
  }
]
```

Status: `active`, `expired`, eller `revoked`.

### Skapa ny token

```
POST /api/tokens
Content-Type: application/json

{"name": "Mitt PowerShell-script"}
```

**Svar (visas EN gång):**
```json
{
  "id": 2,
  "token": "icd_AbCdEf1234567890abcdef1234567890abcdef12",
  "tokenPrefix": "icd_AbCd",
  "name": "Mitt PowerShell-script",
  "expiresAt": "2026-04-21 14:30:00.0",
  "ttlDays": 60
}
```

> **Viktigt:** Kopiera `token`-värdet direkt — det visas aldrig igen!

**Begränsningar:**
- Max 5 aktiva tokens per användare (konfigurerbart: `api.maxTokensPerUser`)
- Token-livslängd: 60 dagar (konfigurerbart: `api.tokenTtlDays`)

### Återkalla token

```
DELETE /api/tokens?id=2
```

**Svar:**
```json
{"success": true}
```

---

## API-endpoints

### Drift Monitor

#### Maskinlista

```
GET /api/drift/machines
```

**Svar:**
```json
[
  {
    "id": 1,
    "hostname": "SRV01",
    "ipAddress": "10.201.21.10",
    "os": "Windows Server 2022",
    "cpuCores": 8,
    "ramGb": 32,
    "environment": "production",
    "location": "Stockholm DC1",
    "notes": null,
    "isActive": true,
    "lastSeen": "2026-02-20 08:00:00",
    "services": [
      {
        "id": 1,
        "serviceName": "Tomcat 9",
        "serviceType": "tomcat",
        "installPath": "C:\\Tomcat9",
        "port": 8443,
        "javaVersion": "21.0.10",
        "status": "running",
        "hostCount": 3
      }
    ],
    "health": {
      "totalHosts": 3,
      "okHosts": 3,
      "warningHosts": 0,
      "errorHosts": 0,
      "stoppedServices": 0
    }
  }
]
```

#### Hosts för en maskin

```
GET /api/drift/machines/{id}/hosts
```

**Svar:**
```json
[
  {
    "id": 1,
    "serviceId": 1,
    "serviceName": "Tomcat 9",
    "hostname": "app.example.com",
    "appName": "InfoCaption",
    "contextPath": "/ic",
    "currentVersion": "8.2.1",
    "healthStatus": "ok",
    "lastHealthCheck": "2026-02-20 09:00:00",
    "sslExpiry": "2026-12-01",
    "notes": null,
    "isActive": true
  }
]
```

#### Sammanfattning

```
GET /api/drift/summary
```

**Svar:**
```json
{
  "totalMachines": 15,
  "activeMachines": 14,
  "totalServices": 22,
  "runningServices": 20,
  "stoppedServices": 2,
  "totalHosts": 48,
  "activeHosts": 45,
  "healthOk": 40,
  "healthWarning": 3,
  "healthError": 2
}
```

---

### CloudGuard — Incidentrapportering

Dedikerat incidentsystem för att rapportera och lösa fel från externa scripts/monitoring.

#### Rapportera incident (PUBLIKT — ingen auth)

```
POST /api/cloudguard/report
Content-Type: application/json

{
  "entityName": "https://example.infocaption.com",
  "entityType": "url",
  "severity": "error",
  "message": "Health check failed: HTTP 500",
  "reportedBy": "allstats.ps1"
}
```

| Fält | Obligatoriskt | Beskrivning |
|------|---------------|-------------|
| `entityName` | **Ja** | URL, servernamn eller tjänstnamn (max 500 tecken) |
| `entityType` | Nej | `url`, `server`, `service`, `certificate` (standard: `service`) |
| `severity` | Nej | `info`, `warning`, `error`, `critical` (standard: `error`) |
| `message` | Nej | Felbeskrivning |
| `reportedBy` | Nej | Vem/vad som rapporterade (max 255 tecken) |

**Svar:**
```json
{
  "id": 42,
  "entityName": "https://example.infocaption.com",
  "entityType": "url",
  "severity": "error",
  "reportedAt": "2026-02-20 14:30:00"
}
```

> **Spara `id`-värdet!** Du behöver det för att lösa incidenten.

#### Lös incident (PUBLIKT — ingen auth)

```
POST /api/cloudguard/resolve
Content-Type: application/json

{
  "id": 42,
  "resolvedBy": "allstats.ps1",
  "resolutionNote": "Server omstartad, health check OK"
}
```

**Svar:**
```json
{
  "success": true,
  "id": 42,
  "resolvedAt": "2026-02-20 15:00:00"
}
```

Om incidenten redan är löst eller inte finns:
```json
{
  "error": "Incident not found or already resolved",
  "id": 42
}
```

#### Lista aktiva incidenter (KRÄVER AUTH)

```
GET /api/cloudguard/active
Authorization: Bearer icd_...
```

**Svar:**
```json
[
  {
    "id": 42,
    "entityName": "https://example.infocaption.com",
    "entityType": "url",
    "severity": "error",
    "message": "Health check failed: HTTP 500",
    "reportedBy": "allstats.ps1",
    "reportedAt": "2026-02-20 14:30:00"
  }
]
```

Sorteras: critical → error → warning → info, sedan senaste först.

---

### Drift Monitor — Upsert (POST)

Alla upsert-endpoints skapar posten om den inte finns, uppdaterar om den redan finns (baserat på unik nyckel).

#### Upsert maskin

```
POST /api/drift/machines
Content-Type: application/json

{
  "hostname": "SRV01",
  "ipAddress": "10.201.21.10",
  "os": "Windows Server 2022",
  "cpuCores": 8,
  "ramGb": 32,
  "environment": "production",
  "location": "Stockholm DC1",
  "notes": "Huvudserver"
}
```

Unik nyckel: `hostname`. Alla fält utom `hostname` är valfria — utelämnade fält behåller sitt nuvarande värde.

#### Upsert tjänst

```
POST /api/drift/services
Content-Type: application/json

{
  "machineHostname": "SRV01",
  "serviceName": "Tomcat 9",
  "serviceType": "tomcat",
  "installPath": "C:\\Tomcat9",
  "port": 8443,
  "javaVersion": "21.0.10",
  "status": "running"
}
```

Unik nyckel: `machineHostname` + `serviceName`. Om maskinen inte finns skapas den automatiskt.

#### Upsert host

```
POST /api/drift/hosts
Content-Type: application/json

{
  "machineHostname": "SRV01",
  "serviceName": "Tomcat 9",
  "hostname": "app.example.com",
  "appName": "InfoCaption",
  "contextPath": "/ic",
  "currentVersion": "8.2.1",
  "healthStatus": "ok",
  "sslExpiry": "2026-12-01"
}
```

Unik nyckel: `hostname`. Om maskin eller tjänst inte finns skapas de automatiskt.

#### Bulk upsert

Skicka maskiner, tjänster och hosts i ett enda anrop:

```
POST /api/drift/bulk
Content-Type: application/json

{
  "machines": [
    { "hostname": "SRV01", "os": "Windows Server 2022", "ipAddress": "10.201.21.10" },
    { "hostname": "SRV02", "os": "Windows Server 2019", "ipAddress": "10.201.21.11" }
  ],
  "services": [
    { "machineHostname": "SRV01", "serviceName": "Tomcat 9", "port": 8443, "status": "running" }
  ],
  "hosts": [
    { "machineHostname": "SRV01", "serviceName": "Tomcat 9", "hostname": "app.example.com", "healthStatus": "ok" }
  ]
}
```

**Svar:**
```json
{
  "machines": { "processed": 2, "errors": 0 },
  "services": { "processed": 1, "errors": 0 },
  "hosts": { "processed": 1, "errors": 0 }
}
```

---

### Customer Stats

#### Hämta aggregerad kunddata

```
GET /api/customer-stats
GET /api/customer-stats?from=2026-01-01&to=2026-02-20
```

#### Historik per server

```
GET /api/customer-stats/history?serverId=5&from=2026-01-01&to=2026-02-20
```

#### Importera statistik (bulk)

```
POST /api/customer-stats/import
Content-Type: application/json
X-API-Key: YOUR_API_KEY

[
  {
    "url": "https://app.example.com/ic",
    "snapshot_date": "2026-02-20",
    "version": "8.2.1",
    "publications_30d": 15,
    "guides_30d": 8,
    ...
  }
]
```

---

### Servrar & Certifikat

```
GET /api/servers           — Serverlista med företag + maskinnamn
GET /api/servers/health    — Hälsokontroll på alla aktiva servrar
GET /api/certificates      — Certifikatlista med förfallodatum
```

---

## Felhantering

Alla API-fel returneras som JSON:

```json
{"error": "Beskrivning av felet"}
```

| HTTP-status | Betydelse |
|-------------|-----------|
| 200 | OK |
| 400 | Felaktig request (saknade fält, ogiltiga värden) |
| 401 | Ej autentiserad (token saknas, utgången eller ogiltig) |
| 403 | Förbjuden (saknar API-behörighet) |
| 404 | Resursen hittades inte |
| 500 | Internt serverfel |

---

## Admin: Hantera API-åtkomst

Som admin kan du styra vilka användare som får generera API-tokens:

1. Gå till **Admin → Användare**
2. Varje användarkort har en **🔑 API**-toggle
3. Aktivera togglen för att ge användaren API-åtkomst
4. Inaktivera togglen för att ta bort åtkomst — **alla aktiva tokens återkallas automatiskt**

### Konfiguration (Admin → Inställningar)

| Nyckel | Standard | Beskrivning |
|--------|----------|-------------|
| `api.tokenTtlDays` | `60` | Token-livslängd i dagar |
| `api.maxTokensPerUser` | `5` | Max antal aktiva tokens per användare |

---

## Snabbstart — PowerShell

```powershell
# 1. Spara din token
$ApiToken = "icd_DinTokenHär..."

# 2. Skapa en hjälpfunktion
function Invoke-DashboardApi {
    param(
        [string]$Path,
        [string]$Method = "GET",
        [object]$Body
    )
    $baseUrl = "https://<DIN-DOMAN>/icDashBoard"
    $headers = @{ "Authorization" = "Bearer $ApiToken" }

    $params = @{
        Uri     = "$baseUrl$Path"
        Method  = $Method
        Headers = $headers
    }
    if ($Body) {
        $headers["Content-Type"] = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }
    Invoke-RestMethod @params
}

# 3. Hämta sammanfattning
$summary = Invoke-DashboardApi -Path "/api/drift/summary"
Write-Host "Maskiner: $($summary.totalMachines), Hosts: $($summary.totalHosts)"

# 4. Rapportera maskin
Invoke-DashboardApi -Path "/api/drift/machines" -Method POST -Body @{
    hostname    = $env:COMPUTERNAME
    os          = (Get-CimInstance Win32_OperatingSystem).Caption
    cpuCores    = (Get-CimInstance Win32_Processor).NumberOfCores
    ramGb       = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
    environment = "production"
}

# 5. CloudGuard — rapportera fel (inget auth krävs!)
$baseUrl = "https://<DIN-DOMAN>/icDashBoard"
$incident = Invoke-RestMethod -Uri "$baseUrl/api/cloudguard/report" -Method POST `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body (@{
        entityName = "https://example.infocaption.com"
        entityType = "url"
        severity   = "error"
        message    = "Health check failed: HTTP 500"
        reportedBy = "mitt-script.ps1"
    } | ConvertTo-Json)
Write-Host "Incident rapporterad: ID $($incident.id)"

# 6. CloudGuard — lös incident (inget auth krävs!)
Invoke-RestMethod -Uri "$baseUrl/api/cloudguard/resolve" -Method POST `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body (@{ id = $incident.id; resolvedBy = "mitt-script.ps1" } | ConvertTo-Json)

# 7. CloudGuard — lista aktiva incidenter (kräver auth)
$active = Invoke-DashboardApi -Path "/api/cloudguard/active"
if ($active.Count -gt 0) {
    Write-Warning "⚠️ $($active.Count) aktiva incidenter!"
    $active | Format-Table id, entityName, severity, reportedAt
}
```
