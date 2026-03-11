---
paths:
  - "Ny mapp/**/*.ps1"
  - "icDashBoard/src/main/webapp/modules/certificates/**"
---

# PowerShell Scripts & Certificate Monitoring

## Scripts (Ny mapp/)

| Script | Purpose |
|--------|---------|
| `allstats.ps1` | Main orchestrator — loops Tomcat hosts, calls getstats.ps1, upserts servers + customer_stats_daily |
| `getstats.ps1` | Queries single customer DB for daily (24h) statistics per metric |
| `backfill-stats.ps1` | Retroactive import: `-From "2025-01-01" -To "2026-02-09"` with `-DryRun` |
| `getdbbroker.ps1` | Parses dbConnectionBroker.cfg → PSCustomObject |
| `getcompanies.ps1` | Fetches company data from SuperOffice API → companies.json |
| `sync-companies.ps1` | Upserts companies into customers, links servers.customer_id |
| `getcerts.ps1` | Scans SSL/TLS keystores on Tomcat servers using keytool |

**Known issue**: `allstats.ps1` has `$TomcatRoots` redefined on line 26-31, overwriting the 15-server list with just one server (10.201.21.18). Remove the second assignment to process all servers.

## Certificate Monitoring (getcerts.ps1)

1. Connects to Tomcat servers via UNC path (`\\server\c$\...`)
2. Finds `.jks`, `.p12`, `.pfx` keystore files in Tomcat conf directories
3. Uses `keytool.exe` (JDK 11 at `C:\jdk-11.0.25+9\bin\keytool.exe`) to list certificates
4. Parses **Swedish-locale** keytool output (Agare, Utfardare, Giltigt fran...till)
5. Calculates days until expiry, upserts into `certificates` table with chain JSON
6. Supports `-DryRun` flag

### Known considerations
- keytool output is Swedish on JDK 11 (cannot override with `-J-Duser.language=en`)
- Timezone regex uses `-creplace` (case-sensitive) to avoid matching month names
- If PKCS12 fails, retries as JKS and vice versa
- Certificate data consumed by `CertificateApiServlet` and `cert_expiry` widget
- Uses keytool (NOT openssl)
