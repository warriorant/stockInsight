param(
    [int]$Port = 5173,
    [string]$HostName = "127.0.0.1"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontendDir = Join-Path $repoRoot "frontend"

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

if ([string]::IsNullOrWhiteSpace($env:VITE_API_BASE_URL)) {
    $env:VITE_API_BASE_URL = "http://localhost:8080/api"
}

$existingFrontend = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($existingFrontend) {
    Write-Host "Frontend already appears to be running on http://$HostName`:$Port"
    Write-Host "Process ID: $($existingFrontend.OwningProcess)"
    exit 0
}

$viteScript = Join-Path $frontendDir "node_modules\vite\bin\vite.js"
$bundledNode = Join-Path $env:USERPROFILE ".cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
$nodeCommand = $null
if (Test-Path $bundledNode) {
    $nodeCommand = (Resolve-Path $bundledNode).Path
} else {
    $nodeFromPath = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($nodeFromPath) {
        $nodeCommand = $nodeFromPath.Source
    }
}

$npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
if (-not $npmCommand) {
    $npmCommand = Get-Command npm -ErrorAction SilentlyContinue
}

Push-Location $frontendDir
try {
    if (-not (Test-Path "node_modules")) {
        if (-not $npmCommand) {
            throw "node_modules is missing and npm was not found. Install Node.js, or ask Codex to install frontend dependencies."
        }

        Write-Host "node_modules not found. Running npm install first."
        & $npmCommand.Source install
    }

    Write-Host "Starting frontend on http://$HostName`:$Port"
    Write-Host "Backend API: $env:VITE_API_BASE_URL"
    Write-Host "Press Ctrl+C to stop the frontend."

    if ((Test-Path $viteScript) -and $nodeCommand) {
        & $nodeCommand $viteScript --host $HostName --port $Port --strictPort
    } elseif ($npmCommand) {
        & $npmCommand.Source run dev -- --host $HostName --port $Port --strictPort
    } else {
        throw "Could not find Node.js or npm. Install Node.js, then run this script again."
    }
} finally {
    Pop-Location
}
