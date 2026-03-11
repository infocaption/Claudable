# AI Setup Guide — InfoCaption Dashboard

Machine-readable setup instructions. Run in PowerShell as Administrator.

## Prerequisites

- Java JDK 21 (Eclipse Adoptium) on PATH
- MySQL 5.7 running on localhost:3306
- Git

## 1. Clone & Navigate

```powershell
git clone <REPO_URL>
cd claudable
```

## 2. MySQL — Create Database & User

```powershell
$mysql = "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"
$rootPw = Read-Host "MySQL root password" -AsSecureString
$rootPwPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootPw))

$dbPassword = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
Write-Host "Generated DB password: $dbPassword" -ForegroundColor Green

& $mysql -u root "-p$rootPwPlain" --default-character-set=utf8mb4 -e @"
CREATE DATABASE IF NOT EXISTS icdashboard CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'icdashboarduser'@'localhost' IDENTIFIED BY '$dbPassword';
GRANT ALL PRIVILEGES ON icdashboard.* TO 'icdashboarduser'@'localhost';
FLUSH PRIVILEGES;
"@
```

## 3. Import Schema

```powershell
& $mysql -u icdashboarduser "-p$dbPassword" icdashboard --default-character-set=utf8mb4 < install-package\install.sql
```

## 4. Create app-secrets.properties

```powershell
$template = "icDashBoard\src\main\webapp\WEB-INF\app-secrets.properties.template"
$target   = "icDashBoard\src\main\webapp\WEB-INF\app-secrets.properties"

(Get-Content $template) -replace 'db.password=CHANGE_ME', "db.password=$dbPassword" |
    Set-Content $target -Encoding UTF8

Write-Host "Created $target with generated password" -ForegroundColor Green
```

## 5. Compile (command-line, no Eclipse)

```powershell
$javaHome = (Get-Command javac).Source | Split-Path | Split-Path
$src = "icDashBoard\src\main\java"
$out = "icDashBoard\build\classes"
$lib = "icDashBoard\src\main\webapp\WEB-INF\lib"
$tomcatLib = "apache-tomcat-9.0.100\lib"

New-Item -ItemType Directory -Force -Path $out | Out-Null

$classpath = @(
    (Get-ChildItem "$lib\*.jar" | ForEach-Object { $_.FullName }) +
    (Get-ChildItem "$tomcatLib\servlet-api.jar","$tomcatLib\jsp-api.jar" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
) -join ";"

$sources = Get-ChildItem -Path $src -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$sourceFile = [System.IO.Path]::GetTempFileName()
$sources | Set-Content $sourceFile

& javac -d $out -cp $classpath -encoding UTF-8 "@$sourceFile"
Remove-Item $sourceFile

Write-Host "Compiled $($sources.Count) Java files to $out" -ForegroundColor Green
```

## 6. Deploy to Tomcat

```powershell
$webapps = "apache-tomcat-9.0.100\webapps\icDashBoard"
$webapp  = "icDashBoard\src\main\webapp"

# Copy webapp files
if (Test-Path $webapps) { Remove-Item $webapps -Recurse -Force }
Copy-Item $webapp $webapps -Recurse

# Copy compiled classes
$classesDir = "$webapps\WEB-INF\classes"
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
Copy-Item "$out\*" $classesDir -Recurse -Force

Write-Host "Deployed to $webapps" -ForegroundColor Green
```

## 7. Start Tomcat

```powershell
$env:JAVA_HOME = $javaHome
$env:CATALINA_HOME = Resolve-Path "apache-tomcat-9.0.100"

& "$env:CATALINA_HOME\bin\startup.bat"

Start-Sleep -Seconds 5
$response = Invoke-WebRequest -Uri "http://localhost:8080/icDashBoard/login" -UseBasicParsing -ErrorAction SilentlyContinue
if ($response.StatusCode -eq 200) {
    Write-Host "Dashboard is running at http://localhost:8080/icDashBoard/" -ForegroundColor Green
} else {
    Write-Host "Tomcat may still be starting. Check logs: apache-tomcat-9.0.100\logs\catalina.out" -ForegroundColor Yellow
}
```

## 8. Create Admin User

Register via browser at `http://localhost:8080/icDashBoard/`, then promote:

```powershell
$email = Read-Host "Your registered email"
& $mysql -u icdashboarduser "-p$dbPassword" icdashboard --default-character-set=utf8mb4 -e "UPDATE users SET is_admin = 1 WHERE email = '$email';"
Write-Host "Admin granted to $email. Re-login to activate." -ForegroundColor Green
```

## All-in-One Script

Copy everything above into a single `.ps1` file, or run this condensed version:

```powershell
# === CONFIGURATION ===
$mysql      = "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"
$repoRoot   = $PWD.Path                                   # run from repo root
$dbPassword = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 24 | % {[char]$_})
$rootPw     = Read-Host "MySQL root password" -AsSecureString
$rootPwPlain= [Runtime.InteropServices.Marshal]::PtrToStringAuto(
                [Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootPw))

# === DATABASE ===
& $mysql -u root "-p$rootPwPlain" --default-character-set=utf8mb4 -e @"
CREATE DATABASE IF NOT EXISTS icdashboard CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'icdashboarduser'@'localhost' IDENTIFIED BY '$dbPassword';
GRANT ALL PRIVILEGES ON icdashboard.* TO 'icdashboarduser'@'localhost';
FLUSH PRIVILEGES;
"@
& $mysql -u icdashboarduser "-p$dbPassword" icdashboard --default-character-set=utf8mb4 < install-package\install.sql

# === SECRETS ===
$tpl = "icDashBoard\src\main\webapp\WEB-INF\app-secrets.properties.template"
$out = "icDashBoard\src\main\webapp\WEB-INF\app-secrets.properties"
(Get-Content $tpl) -replace 'db.password=CHANGE_ME',"db.password=$dbPassword" | Set-Content $out -Encoding UTF8

# === COMPILE ===
$lib = (Get-ChildItem "icDashBoard\src\main\webapp\WEB-INF\lib\*.jar" | % { $_.FullName }) -join ";"
$lib += ";apache-tomcat-9.0.100\lib\servlet-api.jar;apache-tomcat-9.0.100\lib\jsp-api.jar"
$cls = "icDashBoard\build\classes"
New-Item -ItemType Directory -Force -Path $cls | Out-Null
$sf = [IO.Path]::GetTempFileName()
Get-ChildItem "icDashBoard\src\main\java" -Filter "*.java" -Recurse | % { $_.FullName } | Set-Content $sf
& javac -d $cls -cp $lib -encoding UTF-8 "@$sf"
Remove-Item $sf

# === DEPLOY ===
$dest = "apache-tomcat-9.0.100\webapps\icDashBoard"
if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
Copy-Item "icDashBoard\src\main\webapp" $dest -Recurse
New-Item -ItemType Directory -Force "$dest\WEB-INF\classes" | Out-Null
Copy-Item "$cls\*" "$dest\WEB-INF\classes" -Recurse -Force

# === START ===
$env:JAVA_HOME = (Get-Command javac).Source | Split-Path | Split-Path
$env:CATALINA_HOME = Resolve-Path "apache-tomcat-9.0.100"
& "$env:CATALINA_HOME\bin\startup.bat"

Write-Host "`nDone! DB password: $dbPassword" -ForegroundColor Green
Write-Host "Dashboard: http://localhost:8080/icDashBoard/" -ForegroundColor Cyan
```

## Key Facts

| Item | Value |
|------|-------|
| Java | 21 (Eclipse Adoptium) |
| Tomcat | 9.0.100 (in repo: `apache-tomcat-9.0.100/`) |
| MySQL | 5.7, schema `icdashboard`, charset `utf8mb4_unicode_ci` |
| Secrets | `WEB-INF/app-secrets.properties` (gitignored) |
| Build | No Maven/Gradle — javac with JARs from `WEB-INF/lib/` |
| JARs | 12 in `WEB-INF/lib/` (JDBC, BCrypt, SAML, XML, logging) |
| JDBC URL | `connectionCollation=utf8mb4_unicode_ci` required for emoji |
| Session | 30 min timeout, BCrypt work factor 12 |
| SAML SSO | Optional, see `SAML-SETUP.md` |
