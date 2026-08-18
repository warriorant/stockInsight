param(
    [int]$Port = 8080,
    [switch]$Postgres
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $repoRoot "backend"

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return
    }

    Get-Content -Encoding UTF8 $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }

        $name, $value = $line.Split("=", 2)
        $name = $name.Trim()
        $value = $value.Trim().Trim('"').Trim("'")

        if ($name -ne "") {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

Import-DotEnv (Join-Path $repoRoot ".env")

$env:SERVER_PORT = "$Port"
if ($Postgres) {
    $env:SPRING_PROFILES_ACTIVE = "postgres"
} elseif ([string]::IsNullOrWhiteSpace($env:SPRING_PROFILES_ACTIVE)) {
    $env:SPRING_PROFILES_ACTIVE = "local"
}

if ([string]::IsNullOrWhiteSpace($env:CORS_ALLOWED_ORIGINS)) {
    $env:CORS_ALLOWED_ORIGINS = "http://localhost:5173,http://127.0.0.1:5173"
}

$existingBackend = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($existingBackend) {
    Write-Host "Backend already appears to be running on http://localhost:$Port"
    Write-Host "Process ID: $($existingBackend.OwningProcess)"
    Write-Host "Open frontend at http://127.0.0.1:5173, or stop that process before starting another backend."
    exit 0
}

$bundledJava = Join-Path $repoRoot ".tools\jdk-17"
if (Test-Path $bundledJava) {
    $env:JAVA_HOME = (Resolve-Path $bundledJava).Path
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$maven = Join-Path $repoRoot ".tools\maven\bin\mvn.cmd"
if (-not (Test-Path $maven)) {
    $maven = "mvn.cmd"
}

$mavenArgs = @()
$localMavenRepo = Join-Path $repoRoot ".m2"
if (Test-Path $localMavenRepo) {
    $mavenArgs += "-Dmaven.repo.local=$((Resolve-Path $localMavenRepo).Path)"
}
$mavenArgs += "-DskipTests"
$mavenArgs += "package"

$java = "java"
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $javaCandidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $javaCandidate) {
        $java = $javaCandidate
    }
}

$jarPath = Join-Path $backendDir "target\stock-analysis-backend-0.1.0.jar"

Write-Host "Starting backend on http://localhost:$Port"
Write-Host "Spring profile: $env:SPRING_PROFILES_ACTIVE"
if ([string]::IsNullOrWhiteSpace($env:OPENDART_API_KEY)) {
    Write-Host "OPENDART_API_KEY is empty. Financials will use baseline values where DART data is unavailable." -ForegroundColor Yellow
}
Write-Host "Press Ctrl+C to stop the backend."

Push-Location $backendDir
try {
    Write-Host "Building backend jar..."
    & $maven @mavenArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    if (-not (Test-Path $jarPath)) {
        throw "Backend jar was not created: $jarPath"
    }

    Write-Host "Backend jar ready. Launching Spring Boot..."
    & $java -jar $jarPath "--server.port=$Port"
} finally {
    Pop-Location
}
