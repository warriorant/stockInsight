param(
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [switch]$Postgres,
    [switch]$Local,
    [switch]$SkipDocker
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$powershell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"

$backendScript = Join-Path $repoRoot "start-backend.ps1"
$frontendScript = Join-Path $repoRoot "start-frontend.ps1"
$usePostgres = $Postgres -or -not $Local

function Test-PortOpen {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    return $null -ne $connection
}

if ($usePostgres -and -not $SkipDocker) {
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerCommand) {
        Write-Host "Starting PostgreSQL container with Docker Compose..."
        & $dockerCommand.Source compose up -d
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Compose failed. Open Docker Desktop first, then run start-dev.bat again."
        }

        $deadline = (Get-Date).AddSeconds(30)
        while ((Get-Date) -lt $deadline -and -not (Test-PortOpen 5432)) {
            Start-Sleep -Seconds 1
        }

        if (-not (Test-PortOpen 5432)) {
            Write-Host "PostgreSQL port 5432 is not open yet. The backend may need a few more seconds to connect." -ForegroundColor Yellow
        }
    } else {
        Write-Host "Docker command was not found. Start Docker/PostgreSQL manually, or run start-dev.bat -Local." -ForegroundColor Yellow
    }
}

$backendArgs = @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$backendScript`"",
    "-Port", "$BackendPort"
)

if ($usePostgres -or $Postgres) {
    $backendArgs += "-Postgres"
}

$frontendArgs = @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$frontendScript`"",
    "-Port", "$FrontendPort"
)

Write-Host "Opening backend and frontend terminals."
if ($usePostgres) {
    Write-Host "Backend database: PostgreSQL"
} else {
    Write-Host "Backend database: local in-memory mode"
}
Write-Host "Frontend URL: http://127.0.0.1:$FrontendPort"
if ($usePostgres) {
    Write-Host "Chart pattern mode: demo DB seed, AI on-demand disabled by default"
}
Write-Host "Close each terminal, or press Ctrl+C inside it, to stop servers."

Start-Process -FilePath $powershell -ArgumentList $backendArgs -WorkingDirectory $repoRoot
Start-Sleep -Seconds 2
Start-Process -FilePath $powershell -ArgumentList $frontendArgs -WorkingDirectory $repoRoot
