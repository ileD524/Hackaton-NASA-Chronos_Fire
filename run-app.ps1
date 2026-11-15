# Levanta backend Java + frontend Vue desde la raíz del repo.
# Uso:
#   .\run-app.ps1
#   .\run-app.ps1 -BackendOnly
#   .\run-app.ps1 -FrontendOnly
#
# DB_PASSWORD se toma, en este orden:
#   1. variable de entorno DB_PASSWORD
#   2. backend\.env (copiar backend\.env.example a backend\.env)
#   3. prompt interactivo

[CmdletBinding()]
param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend\ChronosFire'

function Get-CommandPath([string]$name) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if (-not $command) { throw "No se encontro $name en PATH." }
    return $command.Source
}

function Import-BackendEnv {
    $envFile = Join-Path $backend '.env'
    if (Test-Path $envFile) {
        Write-Host "Cargando variables desde $envFile"
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
                $value = $Matches[2].Trim().Trim('"').Trim("'")
                [Environment]::SetEnvironmentVariable($Matches[1], $value, 'Process')
            }
        }
    }
    if (-not $env:DB_PASSWORD) {
        $secure = Read-Host 'DB_PASSWORD para PostgreSQL' -AsSecureString
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try {
            $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }
    if (-not $env:DB_PASSWORD) { throw 'DB_PASSWORD es obligatorio.' }
}

function Test-Backend {
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:$BackendPort/api/health" -TimeoutSec 2
        return $health.database -eq $true
    } catch {
        return $false
    }
}

function Wait-Backend {
    for ($i = 0; $i -lt 90; $i++) {
        if (Test-Backend) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Start-BackendProcess {
    $mvn = Get-CommandPath 'mvn.cmd'
    $target = Join-Path $backend 'target'
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    $out = Join-Path $target 'app.log'
    $err = Join-Path $target 'app.err'
    Write-Host 'Iniciando backend Java...'
    return Start-Process -FilePath 'cmd.exe' `
        -ArgumentList @('/c', "`"$mvn`" spring-boot:run") `
        -WorkingDirectory $backend `
        -PassThru `
        -RedirectStandardOutput $out `
        -RedirectStandardError $err
}

function Stop-ProcessTree([System.Diagnostics.Process]$process) {
    if ($process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
}

function Start-Frontend {
    $npm = Get-CommandPath 'npm.cmd'
    if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
        Write-Host 'Instalando dependencias del frontend...'
        Push-Location $frontend
        try { & $npm ci } finally { Pop-Location }
    }
    Write-Host "Frontend: http://127.0.0.1:$FrontendPort"
    Push-Location $frontend
    try {
        & $npm run dev -- --port $FrontendPort
    } finally {
        Pop-Location
    }
}

if ($BackendOnly) {
    Import-BackendEnv
    Write-Host "Backend: http://127.0.0.1:$BackendPort"
    Push-Location $backend
    try { & (Get-CommandPath 'mvn.cmd') spring-boot:run } finally { Pop-Location }
    return
}

if ($FrontendOnly) {
    Start-Frontend
    return
}

Import-BackendEnv
$backendProcess = $null
try {
    if (Test-Backend) {
        Write-Host "Backend ya responde en http://127.0.0.1:$BackendPort"
    } else {
        $backendProcess = Start-BackendProcess
        if (-not (Wait-Backend)) {
            Write-Host 'El backend no respondio. Ultimas lineas del log:'
            Get-Content (Join-Path $backend 'target\app.log') -Tail 40 -ErrorAction SilentlyContinue
            Get-Content (Join-Path $backend 'target\app.err') -Tail 40 -ErrorAction SilentlyContinue
            throw 'No se pudo iniciar el backend.'
        }
        Write-Host "Backend: http://127.0.0.1:$BackendPort"
    }
    Start-Frontend
} finally {
    Stop-ProcessTree $backendProcess
}
