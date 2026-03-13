# ============================================================
# backfill-stats.ps1
# Retroaktivt fyller customer_stats_daily med historisk
# veckovis statistik genom att köra getstats-queryn med
# parametriserat datum istället för NOW().
# Varje snapshot täcker 7 dagar (INTERVAL 7 DAY).
# Loopen stegar veckovis (7 dagar åt gången).
# Kund-DB-frågor körs parallellt via RunspacePool (PowerShell 5.1+).
#
# Användning:
#   .\backfill-stats.ps1 -From "2025-06-01" -To "2026-02-05"
#   .\backfill-stats.ps1 -From "2026-01-01" -To "2026-02-05" -DryRun
#   .\backfill-stats.ps1 -From "2025-06-01" -To "2026-02-05" -ThrottleLimit 10
# ============================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$From,          # Startdatum, t.ex. "2025-01-01"

    [Parameter(Mandatory=$true)]
    [string]$To,            # Slutdatum, t.ex. "2026-02-05"

    [int]$ThrottleLimit = 5, # Max antal parallella kund-DB-frågor

    [switch]$DryRun         # Visa vad som skulle skrivas, utan att skriva
)

# ================================
# Validera datumparametrar
# ================================
try {
    $startDate = [datetime]::ParseExact($From, "yyyy-MM-dd", $null)
    $endDate   = [datetime]::ParseExact($To,   "yyyy-MM-dd", $null)
} catch {
    Write-Error "Ogiltigt datumformat. Använd yyyy-MM-dd, t.ex. 2025-06-01"
    exit 1
}

if ($startDate -gt $endDate) {
    Write-Error "From ($From) kan inte vara efter To ($To)"
    exit 1
}


$totalDays = ($endDate - $startDate).Days + 1
$totalWeeks = [math]::Ceiling($totalDays / 7)
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Backfill: $From -> $To ($totalWeeks veckor)" -ForegroundColor Cyan
if ($DryRun) { Write-Host " *** DRY RUN - inget skrivs till DB ***" -ForegroundColor Yellow }
Write-Host "============================================" -ForegroundColor Cyan

# ================================
# Ladda MySQL DLL
# ================================
if (-not ([System.Management.Automation.PSTypeName]'MySql.Data.MySqlClient.MySqlConnection').Type) {
    try {
        $extDll = Join-Path $PSScriptRoot "System.Threading.Tasks.Extensions.dll"
        if (Test-Path $extDll) {
            Add-Type -Path $extDll -ErrorAction SilentlyContinue
        }
        Add-Type -Path "$PSScriptRoot\MySql.Data.dll" -ErrorAction Stop
    }
    catch [System.Reflection.ReflectionTypeLoadException] {
        # Redan laddad
    }
}

# ================================
# Tomcat-hostar att processa
# ================================
$TomcatRoots = @(
    "\\10.201.21.5\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.8\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.9\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.10\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.11\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.13\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.14\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.16\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.17\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.19\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.22\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.23\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.24\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.25\c$\Program Files\Apache Software Foundation\Tomcat 9.0",
    "\\10.201.21.26\c$\Program Files\Apache Software Foundation\Tomcat 9.0"
)

# DEBUG: Uncomment below to limit to a single server for testing
# $TomcatRoots = @(
#     "\\10.201.21.25\c$\Program Files\Apache Software Foundation\Tomcat 9.0"
# )


# ================================
# Bygg hostlista från server.xml
# (samma logik som allstats.ps1)
# ================================
$hosts = @()
$processedHosts = @{}

foreach ($TomcatRoot in $TomcatRoots) {
    $ComputerName = $TomcatRoot -replace '.*\\\\(.*?)\\.*','$1'
    $serverXml = Join-Path $TomcatRoot "conf\server.xml"

    if (-not (Test-Path $serverXml)) {
        Write-Warning "server.xml hittades inte: $serverXml"
        continue
    }

    [xml]$xml = Get-Content $serverXml
    foreach ($HostNode in $xml.Server.Service.Engine.Host) {
        $hostname = $HostNode.name
        $docBase  = $HostNode.Context.docBase

        # Konvertera lokal sökväg till UNC
        if ($docBase -match '^[a-zA-Z]:\\') {
            $replacement = '\\' + $ComputerName + '\${1}$\'
            $docBase = $docBase -replace '^([a-zA-Z]):\\', $replacement
        }

        # Skippa om docBase saknas
        if ([string]::IsNullOrWhiteSpace($docBase)) { continue }
        # Filtrera bort LOCALHOST_REDIRECT
        if ($docBase -like '*LOCALHOST_REDIRECT*') { continue }
        # Hoppa över dubletter
        if ($processedHosts.ContainsKey($hostname)) { continue }
        $processedHosts[$hostname] = $ComputerName

        $dbCfgPath = Join-Path $docBase "WEB-INF\db\dbConnectionBroker.cfg"
        if (-not (Test-Path $dbCfgPath)) {
            Write-Host "  Skippar $hostname (dbConnectionBroker.cfg saknas)" -ForegroundColor DarkGray
            continue
        }

        # Hämta DB-credentials via getdbbroker.ps1
        $dbBroker = & "$PSScriptRoot\getdbbroker.ps1" -PathDBConnectionbroker $dbCfgPath

        $dbServer   = $dbBroker.dbServer
        $dbLogin    = $dbBroker.dbLogin
        $dbPassword = $dbBroker.dbPassword

        $connStr = "server=$($ComputerName -replace 'jdbc:mysql://|/.*$','');database=$($dbServer -replace 'jdbc:mysql://.*/','');uid=$dbLogin;pwd=$dbPassword;Pooling=false;Connection Timeout=30;"
        # Strip JDBC-style ?characterencoding=utf-8 from database name (MySql.Data.dll treats it as part of db name)
        $connStr = $connStr -replace '\?characterencoding=utf-8', ''

        $hosts += [pscustomobject]@{
            Hostname      = $hostname
            ComputerName  = $ComputerName
            ConnStr       = $connStr
        }
    }
}

Write-Host "Hittade $($hosts.Count) hostar att processa" -ForegroundColor Cyan
foreach ($h in $hosts) {
    Write-Host "  - $($h.Hostname)" -ForegroundColor Gray
}
Write-Host ""

if ($hosts.Count -eq 0) {
    Write-Error "Inga hostar hittades. Kontrollera nätverksåtkomst till Tomcat-servrarna."
    exit 1
}

# ================================
# SQL-query med parametriserat datum
# Samma som getstats.ps1 men @TargetDate istället för NOW()
# Varje snapshot täcker 7 dagar (veckovis insamling)
# ================================
$statsQuery = @"
SELECT
  (SELECT COUNT(*) FROM smartasslog WHERE LogDate >= DATE_SUB(@TargetDate, INTERVAL 7 DAY) AND LogDate <= @TargetDate AND LogType IN (19, 20) AND ID2 NOT IN (SELECT userid FROM smartassuser WHERE username = 'infocaptionserviceuser')) AS Publiseringar_7d,
  (SELECT COUNT(*) FROM guide WHERE PublicationDate >= DATE_SUB(@TargetDate, INTERVAL 7 DAY) AND PublicationDate <= @TargetDate) AS Skapade_Guider_7d,
  (SELECT COUNT(*) FROM smartasslog WHERE LogDate >= DATE_SUB(@TargetDate, INTERVAL 7 DAY) AND LogDate <= @TargetDate AND LogType = 1 AND ID2 NOT IN (SELECT userid FROM smartassuser WHERE username = 'infocaptionserviceuser')) AS Visningar_7d,
  (SELECT COUNT(*) FROM smartasslog WHERE LogDate >= DATE_SUB(@TargetDate, INTERVAL 7 DAY) AND LogDate <= @TargetDate AND LogType = 13 AND ID2 NOT IN (SELECT userid FROM smartassuser WHERE username = 'infocaptionserviceuser')) AS Processvisningar_7d,
  (SELECT COUNT(*) FROM diagram WHERE DateCreated >= DATE_SUB(@TargetDate, INTERVAL 7 DAY) AND DateCreated <= @TargetDate AND DiagramTypeID = 2) AS Processer_Skapade_7d,
  (SELECT COUNT(DISTINCT UserID) FROM smartassuserrights WHERE UserID NOT IN (SELECT userid FROM smartassuser WHERE username = 'infocaptionserviceuser') AND ((Area = 7 AND Level = 1) OR (Area = 8 AND Level = 1) OR (Area = 6 AND Level = 1) OR (Area = 12 AND Level = 1) OR (Area = 13 AND Level = 1) OR (Area = 16 AND Level = 1) OR (Area = 19 AND Level = 1))) AS Antal_Producenter,
  (SELECT COUNT(DISTINCT sur.userid) FROM smartassuserrights sur LEFT JOIN smartassuser su ON sur.userid = su.userid WHERE sur.area = 3 AND sur.level = 1 AND su.username != 'infocaptionserviceuser') AS Antal_Administratorer,
  (SELECT COUNT(*) FROM smartassuser WHERE username != 'infocaptionserviceuser') AS Totalt_Antal_Anvandare,
  (SELECT COUNT(DISTINCT ID2) FROM smartasslog WHERE LogDate >= DATE_SUB(@TargetDate, INTERVAL 7 DAY) AND LogType = 7 AND ID2 NOT IN (SELECT userid FROM smartassuser WHERE username = 'infocaptionserviceuser')) AS Antal_Aktiva_Producenter_Sista_7_Dagar,
  (SELECT COUNT(*) FROM smartasslog sl
    INNER JOIN smartassuserrights sur ON sl.ID2 = sur.UserID
    WHERE sl.LogDate >= DATE_SUB(@TargetDate, INTERVAL 7 DAY) AND sl.LogDate <= @TargetDate
    AND sl.LogType = 6
    AND sl.ID2 NOT IN (SELECT userid FROM smartassuser WHERE username = 'infocaptionserviceuser')
    AND ((sur.Area = 3 AND sur.Level = 1)
      OR (sur.Area = 7 AND sur.Level = 1) OR (sur.Area = 8 AND sur.Level = 1)
      OR (sur.Area = 6 AND sur.Level = 1) OR (sur.Area = 12 AND sur.Level = 1)
      OR (sur.Area = 13 AND sur.Level = 1) OR (sur.Area = 16 AND sur.Level = 1)
      OR (sur.Area = 19 AND sur.Level = 1))
  ) AS Antal_Inloggningar_Prod_Admin,
  (SELECT COUNT(DISTINCT sl.ID2) FROM smartasslog sl
    INNER JOIN smartassuserrights sur ON sl.ID2 = sur.UserID
    WHERE sl.LogDate >= DATE_SUB(@TargetDate, INTERVAL 7 DAY) AND sl.LogDate <= @TargetDate
    AND sl.LogType = 6
    AND sl.ID2 NOT IN (SELECT userid FROM smartassuser WHERE username = 'infocaptionserviceuser')
    AND ((sur.Area = 3 AND sur.Level = 1)
      OR (sur.Area = 7 AND sur.Level = 1) OR (sur.Area = 8 AND sur.Level = 1)
      OR (sur.Area = 6 AND sur.Level = 1) OR (sur.Area = 12 AND sur.Level = 1)
      OR (sur.Area = 13 AND sur.Level = 1) OR (sur.Area = 16 AND sur.Level = 1)
      OR (sur.Area = 19 AND sur.Level = 1))
  ) AS Antal_Unika_Inloggade_Prod_Admin,
  (SELECT COUNT(DISTINCT sl.ID2) FROM smartasslog sl
    INNER JOIN smartassuserrights sur ON sl.ID2 = sur.UserID
    WHERE sl.LogDate >= DATE_SUB(@TargetDate, INTERVAL 7 DAY) AND sl.LogDate <= @TargetDate
    AND sl.LogType IN (19, 20)
    AND sl.ID2 NOT IN (SELECT userid FROM smartassuser WHERE username = 'infocaptionserviceuser')
    AND ((sur.Area = 7 AND sur.Level = 1) OR (sur.Area = 8 AND sur.Level = 1)
      OR (sur.Area = 6 AND sur.Level = 1) OR (sur.Area = 12 AND sur.Level = 1)
      OR (sur.Area = 13 AND sur.Level = 1) OR (sur.Area = 16 AND sur.Level = 1)
      OR (sur.Area = 19 AND sur.Level = 1))
  ) AS Antal_Unika_Publicerande_Prod,
  (SELECT value FROM smartassproperties WHERE name = 'smartass.web.url' LIMIT 1) AS Smartass_Web_Url,
  CONCAT(
    IFNULL((SELECT value FROM smartassproperties WHERE name = 'smartass.version' LIMIT 1), ''),
    '.',
    IFNULL((SELECT value FROM smartassproperties WHERE name = 'smartass.release' LIMIT 1), '')
  ) AS Version
"@
# ================================
# Dashboard-DB connection
# ================================
# TODO: Externalize credentials — read from environment variable or secure config file
# instead of hardcoding password in script. Example:
#   $pwd = $env:ICDASHBOARD_DB_PASSWORD
#   if (-not $pwd) { Write-Error "ICDASHBOARD_DB_PASSWORD not set"; exit 1 }
$dashboardConnStr = "server=localhost;database=icdashboard;uid=icdashboarduser;pwd=ziyDSx3QbP7tfyRo;Pooling=false;Connection Timeout=30;charset=utf8mb4;"

# ================================
# Huvudloop: vecka för vecka
# Fas 1: Fråga alla kund-DBs parallellt
# Fas 2: Skriv resultat till dashboard-DB sekventiellt
# ================================
$dashConn = $null
try {
    if (-not $DryRun) {
        $dashConn = New-Object MySql.Data.MySqlClient.MySqlConnection($dashboardConnStr)
        $dashConn.Open()
        Write-Host "Ansluten till icdashboard" -ForegroundColor Green
    }

    Write-Host "Parallellitet: $ThrottleLimit samtida kund-DB-frågor" -ForegroundColor Cyan

    $weekCounter = 0
    $scriptRoot = $PSScriptRoot
    for ($date = $startDate; $date -le $endDate; $date = $date.AddDays(7)) {
        $weekCounter++
        $dateStr = $date.ToString("yyyy-MM-dd")
        $pct = [math]::Round(($weekCounter / $totalWeeks) * 100)
        Write-Host "`n[$weekCounter/$totalWeeks $pct%] === Vecka $dateStr ===" -ForegroundColor Cyan

        # --- Fas 1: Parallella kund-DB-frågor via RunspacePool ---
        $runspacePool = [runspacefactory]::CreateRunspacePool(1, $ThrottleLimit)
        $runspacePool.Open()

        $jobs = @()
        foreach ($hostInfo in $hosts) {
            $ps = [powershell]::Create().AddScript({
                param($hostInfo, $query, $targetDate, $root)

                # Ladda MySQL DLL i denna runspace
                if (-not ([System.Management.Automation.PSTypeName]'MySql.Data.MySqlClient.MySqlConnection').Type) {
                    try {
                        $extDll = Join-Path $root "System.Threading.Tasks.Extensions.dll"
                        if (Test-Path $extDll) { Add-Type -Path $extDll -ErrorAction SilentlyContinue }
                        Add-Type -Path (Join-Path $root "MySql.Data.dll") -ErrorAction Stop
                    } catch [System.Reflection.ReflectionTypeLoadException] { }
                }

                $custConn = $null
                $reader   = $null
                $cmd      = $null
                try {
                    $custConn = New-Object MySql.Data.MySqlClient.MySqlConnection($hostInfo.ConnStr)
                    $custConn.Open()

                    $cmd = $custConn.CreateCommand()
                    $cmd.CommandText = $query
                    $cmd.Parameters.AddWithValue("@TargetDate", $targetDate) | Out-Null
                    $cmd.CommandTimeout = 60
                    $reader = $cmd.ExecuteReader()

                    if ($reader.Read()) {
                        $url     = $reader["Smartass_Web_Url"]
                        $version = $reader["Version"]

                        if ([string]::IsNullOrWhiteSpace($url)) {
                            return $null
                        }

                        $normalizedUrl = ($url -replace '^https?://', '' -replace '/.*$', '' -replace '/$', '').ToLower()

                        return [pscustomobject]@{
                            Hostname                    = $hostInfo.Hostname
                            Url                         = [string]$url
                            NormalizedUrl               = $normalizedUrl
                            Version                     = [string]$version
                            Publiseringar_7d            = [int]$reader["Publiseringar_7d"]
                            Skapade_Guider_7d           = [int]$reader["Skapade_Guider_7d"]
                            Visningar_7d                = [int]$reader["Visningar_7d"]
                            Processvisningar_7d         = [int]$reader["Processvisningar_7d"]
                            Processer_Skapade_7d        = [int]$reader["Processer_Skapade_7d"]
                            Antal_Producenter           = [int]$reader["Antal_Producenter"]
                            Antal_Administratorer       = [int]$reader["Antal_Administratorer"]
                            Totalt_Antal_Anvandare      = [int]$reader["Totalt_Antal_Anvandare"]
                            Antal_Aktiva_Producenter_6m = [int]$reader["Antal_Aktiva_Producenter_Sista_7_Dagar"]
                            Antal_Inloggningar_Prod_Admin       = [int]$reader["Antal_Inloggningar_Prod_Admin"]
                            Antal_Unika_Inloggade_Prod_Admin    = [int]$reader["Antal_Unika_Inloggade_Prod_Admin"]
                            Antal_Unika_Publicerande_Prod       = [int]$reader["Antal_Unika_Publicerande_Prod"]
                        }
                    }
                }
                catch {
                    # Warnings can't cross runspace boundary, return error info
                    return [pscustomobject]@{ _Error = "$($hostInfo.Hostname) ${targetDate}: $($_.Exception.Message)" }
                }
                finally {
                    if ($reader -ne $null) { $reader.Close(); $reader.Dispose() }
                    if ($cmd -ne $null) { $cmd.Dispose() }
                    if ($custConn -ne $null -and $custConn.State -eq 'Open') { $custConn.Close() }
                    if ($custConn -ne $null) { $custConn.Dispose() }
                }
            }).AddArgument($hostInfo).AddArgument($statsQuery).AddArgument($dateStr).AddArgument($scriptRoot)

            $ps.RunspacePool = $runspacePool
            $jobs += [pscustomobject]@{
                PowerShell = $ps
                Handle     = $ps.BeginInvoke()
            }
        }

        # Samla resultat
        $results = @()
        foreach ($job in $jobs) {
            try {
                $output = $job.PowerShell.EndInvoke($job.Handle)
                foreach ($item in $output) {
                    if ($item -ne $null -and $item.PSObject.Properties['_Error']) {
                        Write-Warning "  $($item._Error)"
                    } elseif ($item -ne $null) {
                        $results += $item
                    }
                }
            } catch {
                Write-Warning "  Runspace-fel: $($_.Exception.Message)"
            } finally {
                $job.PowerShell.Dispose()
            }
        }
        $runspacePool.Close()
        $runspacePool.Dispose()

        # --- Fas 2: Skriv resultat till dashboard-DB (sekventiellt, snabbt) ---
        $validResults = @($results | Where-Object { $_ -ne $null })
        if ($validResults.Count -eq 0) {
            Write-Host "  Inga resultat denna vecka" -ForegroundColor DarkGray
            continue
        }

        foreach ($r in $validResults) {
            if ($DryRun) {
                Write-Host "  $($r.Hostname) ($($r.NormalizedUrl)): vis=$($r.Visningar_7d) pub=$($r.Publiseringar_7d) guider=$($r.Skapade_Guider_7d) logins=$($r.Antal_Inloggningar_Prod_Admin)" -ForegroundColor Yellow
                continue
            }

            try {
                # Upsert server
                $cmdUpsert = $dashConn.CreateCommand()
                $cmdUpsert.CommandText = @"
INSERT INTO servers (url, url_normalized, current_version, first_seen, last_seen)
VALUES (@url, @urlNorm, @version, @snapshotDate, @snapshotDate)
ON DUPLICATE KEY UPDATE
    current_version = IF(@snapshotDate > last_seen, @version, current_version),
    first_seen = LEAST(first_seen, @snapshotDate),
    last_seen = GREATEST(last_seen, @snapshotDate),
    url = @url
"@
                $cmdUpsert.Parameters.AddWithValue("@url", $r.Url) | Out-Null
                $cmdUpsert.Parameters.AddWithValue("@urlNorm", $r.NormalizedUrl) | Out-Null
                $cmdUpsert.Parameters.AddWithValue("@version", $r.Version) | Out-Null
                $cmdUpsert.Parameters.AddWithValue("@snapshotDate", $dateStr) | Out-Null
                $cmdUpsert.ExecuteNonQuery() | Out-Null
                $cmdUpsert.Dispose()

                # Hämta server ID
                $cmdGetId = $dashConn.CreateCommand()
                $cmdGetId.CommandText = "SELECT id FROM servers WHERE url_normalized = @urlNorm"
                $cmdGetId.Parameters.AddWithValue("@urlNorm", $r.NormalizedUrl) | Out-Null
                $serverId = $cmdGetId.ExecuteScalar()
                $cmdGetId.Dispose()

                if (-not $serverId) {
                    Write-Warning "  Kunde inte hitta server ID för $($r.NormalizedUrl)"
                    continue
                }

                # Update gauge fields on servers table
                $cmdGauge = $dashConn.CreateCommand()
                $cmdGauge.CommandText = @"
UPDATE servers SET
    antal_producenter = @gp1,
    antal_administratorer = @gp2,
    totalt_antal_anvandare = @gp3
WHERE id = @gServId
"@
                $cmdGauge.Parameters.AddWithValue("@gp1", $r.Antal_Producenter) | Out-Null
                $cmdGauge.Parameters.AddWithValue("@gp2", $r.Antal_Administratorer) | Out-Null
                $cmdGauge.Parameters.AddWithValue("@gp3", $r.Totalt_Antal_Anvandare) | Out-Null
                $cmdGauge.Parameters.AddWithValue("@gServId", $serverId) | Out-Null
                $cmdGauge.ExecuteNonQuery() | Out-Null
                $cmdGauge.Dispose()

                # Insert/update veckovis statistik
                $cmdInsert = $dashConn.CreateCommand()
                $cmdInsert.CommandText = @"
INSERT INTO customer_stats_daily (
    server_id, snapshot_date,
    publiseringar_7d, skapade_guider_7d,
    visningar_7d, processvisningar_7d,
    processer_skapade_7d,
    antal_aktiva_producenter_6m,
    antal_inloggningar_prod_admin, antal_unika_inloggade_prod_admin,
    antal_unika_publicerande_prod,
    version
) VALUES (
    @servId, @snapshotDate,
    @p1, @p2, @p3, @p4, @p5,
    @p6, @p7, @p8, @p9, @ver
) ON DUPLICATE KEY UPDATE
    publiseringar_7d = @p1, skapade_guider_7d = @p2,
    visningar_7d = @p3, processvisningar_7d = @p4,
    processer_skapade_7d = @p5,
    antal_aktiva_producenter_6m = @p6,
    antal_inloggningar_prod_admin = @p7, antal_unika_inloggade_prod_admin = @p8,
    antal_unika_publicerande_prod = @p9,
    version = @ver
"@
                $cmdInsert.Parameters.AddWithValue("@servId", $serverId) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@snapshotDate", $dateStr) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@p1",  $r.Publiseringar_7d) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@p2",  $r.Skapade_Guider_7d) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@p3",  $r.Visningar_7d) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@p4",  $r.Processvisningar_7d) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@p5",  $r.Processer_Skapade_7d) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@p6",  $r.Antal_Aktiva_Producenter_6m) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@p7",  $r.Antal_Inloggningar_Prod_Admin) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@p8",  $r.Antal_Unika_Inloggade_Prod_Admin) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@p9",  $r.Antal_Unika_Publicerande_Prod) | Out-Null
                $cmdInsert.Parameters.AddWithValue("@ver", $r.Version) | Out-Null
                $cmdInsert.ExecuteNonQuery() | Out-Null
                $cmdInsert.Dispose()

                Write-Host "  $($r.Hostname): OK (vis=$($r.Visningar_7d) pub=$($r.Publiseringar_7d))" -ForegroundColor Green
            }
            catch {
                Write-Warning "  $($r.Hostname) $dateStr : $($_.Exception.Message)"
            }
        }

        Write-Host "  $($validResults.Count)/$($hosts.Count) hostar klara" -ForegroundColor DarkCyan
    }

    Write-Host "`n============================================" -ForegroundColor Cyan
    Write-Host " Backfill klar! $weekCounter veckor processade." -ForegroundColor Green
    if ($DryRun) { Write-Host " (DRY RUN - inget skrevs)" -ForegroundColor Yellow }
    Write-Host "============================================" -ForegroundColor Cyan

}
catch {
    Write-Error "Oväntat fel: $($_.Exception.Message)"
}
finally {
    if ($dashConn -ne $null -and $dashConn.State -eq 'Open') { $dashConn.Close() }
    if ($dashConn -ne $null) { $dashConn.Dispose() }
}
