# e-Barangay Management System — Development Setup Script
# Run from the eBMS project root: .\scripts\setup.ps1
# PowerShell 5.1+ required.

param(
    [switch]$InitDb    # Pass -InitDb to also run the create-db.sql script
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

Write-Host ""
Write-Host "=== e-Barangay Management System Setup ===" -ForegroundColor Cyan
Write-Host ""

# ─── 1. Check Java 21+ ────────────────────────────────────────────────────────
Write-Host "Step 1: Checking Java version..." -ForegroundColor Yellow

try {
    $javaVersionOutput = & java -version 2>&1 | Select-String -Pattern "\d+\.\d+"
    $javaVersion = [regex]::Match($javaVersionOutput, '"(\d+)').Groups[1].Value
    $javaMajor = [int]$javaVersion

    if ($javaMajor -lt 21) {
        Write-Host "  ERROR: Java 21 or higher is required. Found: Java $javaMajor" -ForegroundColor Red
        exit 1
    }
    Write-Host "  OK — Java $javaMajor detected." -ForegroundColor Green
} catch {
    Write-Host "  ERROR: Java not found in PATH. Install JDK 21+." -ForegroundColor Red
    exit 1
}

# ─── 2. Generate dev keystore if missing ──────────────────────────────────────
Write-Host ""
Write-Host "Step 2: Checking dev keystore..." -ForegroundColor Yellow

$KeystorePath = Join-Path $ProjectRoot "ebms-server\src\main\resources\dev-keystore.p12"

if (Test-Path $KeystorePath) {
    Write-Host "  OK — dev-keystore.p12 already exists." -ForegroundColor Green
} else {
    Write-Host "  Keystore not found. Generating dev-keystore.p12..." -ForegroundColor Yellow

    $keytool = Join-Path ([System.IO.Path]::GetDirectoryName((Get-Command java).Source)) "keytool.exe"
    if (-not (Test-Path $keytool)) {
        $keytool = "keytool"   # fall back to PATH
    }

    & $keytool `
        -genkeypair -alias ebms -keyalg RSA -keysize 2048 `
        -keystore $KeystorePath -storetype PKCS12 -validity 365 `
        -storepass changeit `
        -dname "CN=localhost,OU=eBMS,O=Barangay,L=Manila,ST=Metro Manila,C=PH" `
        -noprompt

    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ERROR: keytool failed. Check that keytool is available." -ForegroundColor Red
        exit 1
    }
    Write-Host "  OK — dev-keystore.p12 generated at $KeystorePath" -ForegroundColor Green
    Write-Host "  NOTE: Set SSL_KEY_STORE_PASSWORD=changeit (dev default)." -ForegroundColor DarkYellow
}

# ─── 3. Environment variable checklist ────────────────────────────────────────
Write-Host ""
Write-Host "Step 3: Required environment variables" -ForegroundColor Yellow
Write-Host "  The following env vars must be set before starting the server."
Write-Host "  (Defaults shown in brackets — change for production!)"
Write-Host ""

$envVars = @(
    @{ Name = "DB_HOST";              Default = "localhost";         Required = $false },
    @{ Name = "DB_PORT";              Default = "3306";              Required = $false },
    @{ Name = "DB_NAME";              Default = "ebms";              Required = $false },
    @{ Name = "DB_USERNAME";          Default = "ebms_app";          Required = $true  },
    @{ Name = "DB_PASSWORD";          Default = "(no default — set this!)"; Required = $true  },
    @{ Name = "JWT_SECRET";           Default = "(no default — must be 256-bit random)"; Required = $true  },
    @{ Name = "SSL_ENABLED";          Default = "true";              Required = $false },
    @{ Name = "SSL_KEY_STORE_PASSWORD"; Default = "changeit (dev only)"; Required = $false },
    @{ Name = "DOCUMENT_STORAGE_PATH"; Default = "./documents";       Required = $false }
)

$missing = @()
foreach ($v in $envVars) {
    $val = [System.Environment]::GetEnvironmentVariable($v.Name)
    if ($val) {
        Write-Host ("  [SET]     {0,-30} = {1}" -f $v.Name, "***") -ForegroundColor Green
    } else {
        $color = if ($v.Required) { "Red" } else { "DarkYellow" }
        Write-Host ("  [MISSING] {0,-30}   default: {1}" -f $v.Name, $v.Default) -ForegroundColor $color
        if ($v.Required) { $missing += $v.Name }
    }
}

if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "  WARNING: $($missing.Count) required variable(s) not set: $($missing -join ', ')" -ForegroundColor Red
} else {
    Write-Host ""
    Write-Host "  All required env vars are set." -ForegroundColor Green
}

# ─── 4. Check MySQL service ───────────────────────────────────────────────────
Write-Host ""
Write-Host "Step 4: Checking MySQL service (MySQL80)..." -ForegroundColor Yellow

try {
    $svc = Get-Service -Name "MySQL80" -ErrorAction SilentlyContinue
    if ($null -eq $svc) {
        Write-Host "  WARNING: MySQL80 service not found. Is MySQL installed?" -ForegroundColor DarkYellow
    } elseif ($svc.Status -eq "Running") {
        Write-Host "  OK — MySQL80 is running." -ForegroundColor Green
    } else {
        Write-Host "  WARNING: MySQL80 service exists but is not running (Status: $($svc.Status))." -ForegroundColor Red
        Write-Host "  Start it with:  Start-Service MySQL80" -ForegroundColor DarkYellow
    }
} catch {
    Write-Host "  WARNING: Could not check MySQL80 service status." -ForegroundColor DarkYellow
}

# ─── 5. Optionally run create-db.sql ─────────────────────────────────────────
if ($InitDb) {
    Write-Host ""
    Write-Host "Step 5: Running initial database setup (create-db.sql)..." -ForegroundColor Yellow

    $SqlFile = Join-Path $ProjectRoot "docs\setup\create-db.sql"
    $MySqlExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

    if (-not (Test-Path $MySqlExe)) {
        Write-Host "  ERROR: mysql.exe not found at $MySqlExe" -ForegroundColor Red
        Write-Host "  Adjust the path in this script or run the SQL manually." -ForegroundColor DarkYellow
        exit 1
    }

    $rootUser = Read-Host "  Enter MySQL root username (default: root)"
    if (-not $rootUser) { $rootUser = "root" }
    $rootPass = Read-Host "  Enter MySQL root password" -AsSecureString
    $plainPass = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootPass))

    & $MySqlExe -u $rootUser -p"$plainPass" < $SqlFile

    if ($LASTEXITCODE -eq 0) {
        Write-Host "  OK — Database initialised successfully." -ForegroundColor Green
    } else {
        Write-Host "  ERROR: mysql.exe exited with code $LASTEXITCODE." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host ""
    Write-Host "Step 5: Database init skipped." -ForegroundColor DarkGray
    Write-Host "  Run with -InitDb to execute docs\setup\create-db.sql automatically."
}

# ─── Done ─────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Setup complete ===" -ForegroundColor Cyan
Write-Host "Next steps:"
Write-Host "  1. Set any missing environment variables listed above."
Write-Host "  2. Run Flyway migrations (automatic on first server start)."
Write-Host "  3. Start the server: mvn spring-boot:run (from ebms-server/ directory)."
Write-Host ""
