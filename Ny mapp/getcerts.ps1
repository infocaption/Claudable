# ============================================================
# getcerts.ps1
# Samlar in SSL-certifikatinformation från alla Tomcat-servrar
# genom att:
#   1. Läsa server.xml för att hitta keystore-filer och lösenord
#   2. Använda keytool för att extrahera certifikatdetaljer
#   3. Skriva resultatet till icdashboard.certificates-tabellen
#
# Kräver: JDK (keytool), MySql.Data.dll
#
# Användning:
#   .\getcerts.ps1
#   .\getcerts.ps1 -DryRun
# ============================================================

param(
    [switch]$DryRun,    # Visa vad som hittas utan att skriva till DB
    [switch]$Debug      # Visa rå keytool-output för första keystore (felsökning)
)

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
# Keytool-sökväg
# ================================
$keytoolPath = "C:\JAVA\jdk-21.0.8+9\bin\keytool.exe"
if (-not (Test-Path $keytoolPath)) {
    # Fallback till Eclipse Adoptium default install path
    $keytoolPath = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\keytool.exe"
}
if (-not (Test-Path $keytoolPath)) {
    Write-Error "keytool.exe hittades inte!"
    exit 1
}
Write-Host "Använder keytool: $keytoolPath" -ForegroundColor DarkGray

# ================================
# Tomcat-servrar (samma lista som allstats/backfill)
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

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Certifikatskanning av $($TomcatRoots.Count) Tomcat-servrar" -ForegroundColor Cyan
if ($DryRun) { Write-Host " *** DRY RUN - inget skrivs till DB ***" -ForegroundColor Yellow }
Write-Host "============================================" -ForegroundColor Cyan

# ================================
# Funktion: Parsa keytool -list output (svensk locale)
# Keytool output med svensk JDK har rader som:
#   Ägare: CN=*.infocaption.com, O=InfoCaption AB, L=Stockholm, C=SE
#   Utfärdare: CN=Thawte TLS RSA CA G1, ...
#   Serienummer: 4d7c6c73f5993557a4339684070b84d
#   Giltigt från: Mon Apr 07 02:00:00 CEST 2025, till: Sat May 09 01:59:59 CEST 2026
# ================================
function Parse-KeytoolOutput {
    param([string[]]$Output)

    $results = @()
    $current = $null

    foreach ($rawLine in $Output) {
        $line = $rawLine.ToString().Trim()

        # Nytt certifikat i kedjan (vi vill bara ha [1] = leaf cert)
        # Stöd för både svenska (Certifikat) och engelska (Certificate) — JDK 21 ger engelska
        if ($line -match '(Certifikat|Certificate)\[1\]:') {
            $current = @{ Owner = ''; Issuer = ''; Serial = ''; ValidFrom = $null; ValidTo = $null }
        }
        # Om vi träffar [2] eller [3] slutar vi (det är CA-certs i kedjan)
        if ($line -match '(Certifikat|Certificate)\[\d+\]:' -and $line -notmatch '(Certifikat|Certificate)\[1\]:') {
            if ($current -and $current.ValidTo) {
                $results += $current
            }
            $current = $null
        }

        if (-not $current) { continue }

        # Owner/Ägare (stöd för båda svenska och engelska)
        if ($line -match '^(Owner|.gare):\s*(.+)$') {
            $current.Owner = $Matches[2].Trim()
        }
        # Issuer/Utfärdare
        if ($line -match '^(Issuer|Utf.rdare):\s*(.+)$') {
            $current.Issuer = $Matches[2].Trim()
        }
        # Serial/Serienummer
        if ($line -match '^(Serial number|Serienummer):\s*(.+)$') {
            $current.Serial = $Matches[2].Trim()
        }
        # Validity/Giltigt (svensk: "Giltigt från: ... , till: ...")
        # Engelskt JDK 21: "Valid from: ... until: ..." (INGEN komma före until)
        if ($line -match '(Giltigt fr.n|Valid from):\s*(.+?),?\s+(till|until):\s*(.+)$') {
            $fromStr = $Matches[2].Trim()
            $toStr   = $Matches[4].Trim()

            # Keytool datum format: "Mon Apr 07 02:00:00 CEST 2025"
            # Ta bort tidszon som sitter mellan HH:mm:ss och yyyy
            # Viktigt: Använd -creplace (case-sensitive) för att inte matcha månadsnamn (May, Jun etc.)
            $fromClean = $fromStr -creplace '\s+(CEST|CET|GMT|UTC|MSK|EET|EEST|WET|WEST|BST|IST|JST|CST|EST|PST|MST|CDT|EDT|PDT|MDT)\s+', ' '
            $toClean   = $toStr   -creplace '\s+(CEST|CET|GMT|UTC|MSK|EET|EEST|WET|WEST|BST|IST|JST|CST|EST|PST|MST|CDT|EDT|PDT|MDT)\s+', ' '

            # Format: "Mon Apr 07 02:00:00 2025"  eller "Thu Dec 11 10:51:26 2025"
            $formats = @(
                'ddd MMM dd HH:mm:ss yyyy',
                'ddd MMM  d HH:mm:ss yyyy',
                'ddd MMM d HH:mm:ss yyyy'
            )

            foreach ($fmt in $formats) {
                try {
                    $current.ValidFrom = [datetime]::ParseExact($fromClean, $fmt, [System.Globalization.CultureInfo]::InvariantCulture)
                } catch { }
                try {
                    $current.ValidTo = [datetime]::ParseExact($toClean, $fmt, [System.Globalization.CultureInfo]::InvariantCulture)
                } catch { }
                if ($current.ValidTo) { break }
            }
        }
    }

    # Fånga sista certifikatet om det inte avslutades av [2]
    if ($current -and $current.ValidTo) {
        $results += $current
    }

    return $results
}

# ================================
# Funktion: Detektera keystore-typ
# ================================
function Get-KeystoreType {
    param([string]$FilePath)

    $ext = [System.IO.Path]::GetExtension($FilePath).ToLower()
    switch ($ext) {
        '.jks'  { return 'JKS' }
        '.p12'  { return 'PKCS12' }
        '.pfx'  { return 'PKCS12' }
        default {
            # Utan filändelse, testa som PKCS12 först (vanligast)
            return 'PKCS12'
        }
    }
}

# ================================
# Samla in certifikat
# ================================
$certResults = @()
$processedKeystores = @{}  # Undvik att läsa samma keystore flera gånger

foreach ($TomcatRoot in $TomcatRoots) {
    $ComputerName = $TomcatRoot -replace '.*\\\\(.*?)\\.*','$1'
    $serverXml = Join-Path $TomcatRoot "conf\server.xml"

    if (-not (Test-Path $serverXml)) {
        Write-Warning "server.xml hittades inte: $serverXml"
        continue
    }

    Write-Host "`n--- $ComputerName ---" -ForegroundColor Cyan

    [xml]$xml = Get-Content $serverXml

    # Hitta alla SSL-aktiverade Connectors
    foreach ($connector in $xml.Server.Service.Connector) {
        if ($connector.SSLEnabled -ne 'true') { continue }

        # SSLHostConfig kan vara ett enda element eller en array
        $sslConfigs = @($connector.SSLHostConfig)
        foreach ($sslConfig in $sslConfigs) {
            if (-not $sslConfig) { continue }

            $hostNamePattern = $sslConfig.hostName
            if (-not $hostNamePattern) { $hostNamePattern = $connector.defaultSSLHostConfigName }

            # Certificate kan vara ett enda element eller en array
            $certs = @($sslConfig.Certificate)
            foreach ($cert in $certs) {
                if (-not $cert) { continue }

                $keystoreFile = $cert.certificateKeystoreFile
                $keystorePassword = $cert.certificateKeystorePassword

                if (-not $keystoreFile) {
                    Write-Host "  Skippar Certificate utan keystoreFile" -ForegroundColor DarkGray
                    continue
                }

                # Konvertera lokal sökväg till UNC-sökväg
                $uncKeystorePath = $keystoreFile
                if ($keystoreFile -match '^[a-zA-Z]:\\') {
                    $driveLetter = $keystoreFile.Substring(0, 1)
                    $restOfPath = $keystoreFile.Substring(3)
                    $uncKeystorePath = "\\$ComputerName\$driveLetter`$\$restOfPath"
                }

                # Hoppa över om vi redan skannat denna keystore
                $cacheKey = "$ComputerName|$keystoreFile"
                if ($processedKeystores.ContainsKey($cacheKey)) {
                    Write-Host "  Skippar dublett: $keystoreFile" -ForegroundColor DarkGray
                    continue
                }
                $processedKeystores[$cacheKey] = $true

                if (-not (Test-Path $uncKeystorePath)) {
                    Write-Warning "  Keystore-fil hittades inte: $uncKeystorePath"
                    continue
                }

                Write-Host "  Keystore: $keystoreFile ($hostNamePattern)" -ForegroundColor White

                # Kopiera till lokal temp-fil (keytool kan ha problem med UNC-sökvägar)
                $ksType = Get-KeystoreType $keystoreFile
                $tempExt = if ($ksType -eq 'JKS') { '.jks' } else { '.p12' }
                $tempFile = [System.IO.Path]::GetTempFileName() + $tempExt

                try {
                    Copy-Item $uncKeystorePath $tempFile -Force

                    # Kör keytool (svensk locale, parsern stöder svenska nyckelord)
                    $keytoolArgs = @(
                        '-list', '-v',
                        '-keystore', $tempFile,
                        '-storepass', $keystorePassword,
                        '-storetype', $ksType
                    )
                    $ktOutput = & $keytoolPath @keytoolArgs 2>&1

                    # Om PKCS12 misslyckas, prova JKS och vice versa
                    $hasError = $false
                    foreach ($errLine in $ktOutput) {
                        if ($errLine.ToString() -match 'keytool error|IOException') { $hasError = $true; break }
                    }
                    if ($hasError -and $ksType -eq 'PKCS12') {
                        Write-Host "    PKCS12 misslyckades, provar JKS..." -ForegroundColor DarkGray
                        $keytoolArgs[6] = 'JKS'
                        $ktOutput = & $keytoolPath @keytoolArgs 2>&1
                    } elseif ($hasError -and $ksType -eq 'JKS') {
                        Write-Host "    JKS misslyckades, provar PKCS12..." -ForegroundColor DarkGray
                        $keytoolArgs[6] = 'PKCS12'
                        $ktOutput = & $keytoolPath @keytoolArgs 2>&1
                    }

                    # Debug: dump raw keytool output for first keystore
                    if ($Debug -and $certResults.Count -eq 0 -and (-not $script:debugDumped)) {
                        $script:debugDumped = $true
                        Write-Host "`n    === DEBUG: Rå keytool-output (första 40 rader) ===" -ForegroundColor Magenta
                        $ktOutput | Select-Object -First 40 | ForEach-Object {
                            Write-Host "    | $($_.ToString())" -ForegroundColor DarkGray
                        }
                        Write-Host "    === SLUT DEBUG ===" -ForegroundColor Magenta
                    }

                    $parsed = Parse-KeytoolOutput $ktOutput

                    if ($parsed.Count -eq 0) {
                        Write-Warning "    Kunde inte parsa certifikatinfo från $keystoreFile"
                        # Visa relevanta rader för felsökning
                        $relevantLines = $ktOutput | Where-Object {
                            $s = $_.ToString()
                            $s -match 'exception|error|Owner|Issuer|Valid|Giltig|gare|rdare'
                        } | Select-Object -First 5
                        if ($relevantLines) {
                            foreach ($rl in $relevantLines) { Write-Warning "    $rl" }
                        }
                        continue
                    }

                    foreach ($p in $parsed) {
                        $daysLeft = ($p.ValidTo - (Get-Date)).Days
                        $statusColor = if ($daysLeft -le 0) { "Red" } elseif ($daysLeft -le 30) { "Yellow" } else { "Green" }
                        $statusText  = if ($daysLeft -le 0) { "UTGATT" } elseif ($daysLeft -le 30) { "SNART" } else { "OK" }

                        Write-Host "    Subject: $($p.Owner)" -ForegroundColor White
                        Write-Host "    Giltig: $($p.ValidFrom.ToString('yyyy-MM-dd')) -> $($p.ValidTo.ToString('yyyy-MM-dd')) ($daysLeft dagar kvar)" -ForegroundColor $statusColor
                        Write-Host "    Status: $statusText" -ForegroundColor $statusColor

                        $certResults += [pscustomobject]@{
                            ServerIp        = $ComputerName
                            KeystorePath    = $keystoreFile
                            HostnamePattern = $hostNamePattern
                            Subject         = $p.Owner
                            Issuer          = $p.Issuer
                            SerialNumber    = $p.Serial
                            ValidFrom       = $p.ValidFrom.ToString('yyyy-MM-dd')
                            ValidTo         = $p.ValidTo.ToString('yyyy-MM-dd')
                            DaysLeft        = $daysLeft
                            Status          = $statusText
                        }
                    }
                }
                catch {
                    Write-Warning "    Fel vid läsning av $keystoreFile : $($_.Exception.Message)"
                }
                finally {
                    if (Test-Path $tempFile) { Remove-Item $tempFile -Force -ErrorAction SilentlyContinue }
                }
            }
        }
    }
}

# ================================
# Sammanfattning
# ================================
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host " Hittade $($certResults.Count) certifikat totalt" -ForegroundColor Cyan

$expired  = ($certResults | Where-Object { $_.DaysLeft -le 0 }).Count
$critical = ($certResults | Where-Object { $_.DaysLeft -gt 0 -and $_.DaysLeft -le 30 }).Count
$ok       = ($certResults | Where-Object { $_.DaysLeft -gt 30 }).Count

if ($expired -gt 0)  { Write-Host "  UTGANGNA: $expired" -ForegroundColor Red }
if ($critical -gt 0) { Write-Host "  KRITISKA (< 30 dagar): $critical" -ForegroundColor Yellow }
Write-Host "  OK: $ok" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan

if ($DryRun) {
    Write-Host "`n*** DRY RUN - inget skrevs till DB ***" -ForegroundColor Yellow
    $certResults | Format-Table -AutoSize
    exit 0
}

if ($certResults.Count -eq 0) {
    Write-Host "Inga certifikat hittades. Inget att skriva." -ForegroundColor Yellow
    exit 0
}

# ================================
# Skriv till icdashboard DB
# ================================
# TODO: Externalize credentials — read from environment variable or secure config file
# instead of hardcoding password in script. Example:
#   $pwd = $env:ICDASHBOARD_DB_PASSWORD
#   if (-not $pwd) { Write-Error "ICDASHBOARD_DB_PASSWORD not set"; exit 1 }
$dashboardConnStr = "server=localhost;database=icdashboard;uid=icdashboarduser;pwd=ziyDSx3QbP7tfyRo;Pooling=false;Connection Timeout=30;charset=utf8mb4;"

try {
    $dashConn = New-Object MySql.Data.MySqlClient.MySqlConnection($dashboardConnStr)
    $dashConn.Open()
    Write-Host "`nAnsluten till icdashboard" -ForegroundColor Green

    $writeCount = 0
    foreach ($cert in $certResults) {
        $cmdUpsert = $dashConn.CreateCommand()
        $cmdUpsert.CommandText = @"
INSERT INTO certificates (server_ip, keystore_path, hostname_pattern, subject, issuer, serial_number, valid_from, valid_to)
VALUES (@serverIp, @keystorePath, @hostnamePattern, @subject, @issuer, @serial, @validFrom, @validTo)
ON DUPLICATE KEY UPDATE
    hostname_pattern = @hostnamePattern,
    subject = @subject,
    issuer = @issuer,
    serial_number = @serial,
    valid_from = @validFrom,
    valid_to = @validTo,
    last_checked = CURRENT_TIMESTAMP
"@
        $cmdUpsert.Parameters.AddWithValue("@serverIp", $cert.ServerIp) | Out-Null
        $cmdUpsert.Parameters.AddWithValue("@keystorePath", $cert.KeystorePath) | Out-Null
        $cmdUpsert.Parameters.AddWithValue("@hostnamePattern", $cert.HostnamePattern) | Out-Null
        $cmdUpsert.Parameters.AddWithValue("@subject", $cert.Subject) | Out-Null
        $cmdUpsert.Parameters.AddWithValue("@issuer", $cert.Issuer) | Out-Null
        $cmdUpsert.Parameters.AddWithValue("@serial", $cert.SerialNumber) | Out-Null
        $cmdUpsert.Parameters.AddWithValue("@validFrom", $cert.ValidFrom) | Out-Null
        $cmdUpsert.Parameters.AddWithValue("@validTo", $cert.ValidTo) | Out-Null
        $cmdUpsert.ExecuteNonQuery() | Out-Null
        $cmdUpsert.Dispose()
        $writeCount++
    }

    Write-Host "Skrev $writeCount certifikat till icdashboard" -ForegroundColor Green
}
catch {
    Write-Warning "DB-skrivning misslyckades: $($_.Exception.Message)"
}
finally {
    if ($dashConn -ne $null -and $dashConn.State -eq 'Open') { $dashConn.Close() }
    if ($dashConn -ne $null) { $dashConn.Dispose() }
}
