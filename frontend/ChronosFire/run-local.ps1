# Frontend dev server para Windows. El backend Java debe estar en :8080.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path 'node_modules')) {
    npm ci
}
Write-Host 'Backend Java esperado en http://127.0.0.1:8080'
npm run dev
