# start-ebms.ps1 — Starts the eBMS server then the desktop client.
# Usage: Right-click → "Run with PowerShell", or: powershell -ExecutionPolicy Bypass -File start-ebms.ps1

$MVN  = "C:\Program Files\NetBeans-25\netbeans\java\maven\bin\mvn.cmd"
$ROOT = $PSScriptRoot

# ── Database credentials ───────────────────────────────────────────────────────
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "test123"

# ── Kill any process already on port 8080 ─────────────────────────────────────
$existing = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "Port 8080 in use — stopping existing process (PID $($existing.OwningProcess))..."
    Stop-Process -Id $existing.OwningProcess -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

# ── Start the server in a new window ──────────────────────────────────────────
Write-Host "Starting eBMS server..."
$serverArgs = "-f `"$ROOT\ebms-server\pom.xml`" spring-boot:run"
$server = Start-Process -FilePath $MVN -ArgumentList $serverArgs -PassThru -WindowStyle Normal

# ── Wait until port 8080 is accepting connections (max 120 s) ─────────────────
Write-Host "Waiting for server on port 8080 (up to 120 s)..."
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.ConnectAsync("127.0.0.1", 8080).Wait(500) | Out-Null
        if ($tcp.Connected) { $tcp.Close(); $ready = $true; break }
        $tcp.Close()
    } catch {}
}

if (-not $ready) {
    Write-Host "ERROR: Server did not start within 120 seconds. Check the server window for errors."
    exit 1
}

Write-Host "Server is ready. Launching desktop..."
& $MVN -f "$ROOT\ebms-desktop\pom.xml" javafx:run
