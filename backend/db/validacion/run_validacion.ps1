# ============================================================================
# Chronos Fire - Runner de validacion Â§6.8
#
# Uso:
#   $env:PGPASSWORD = 'postgres'
#   .\backend\db\validacion\run_validacion.ps1 -SkipBase -ViirsSensors VIIRS_SNPP
#
# Parametros:
#   -SkipBase        no re-ejecuta V0-V3
#   -SkipSweep       no ejecuta V4
#   -Summarize       solo consolida los archivos existentes
#   -ViirsSensors    lista de sensores VIIRS a barrer
#   -FromYear/-ToYear rango de anios
#   -MaxHours        24 = mismo dia; 3 = ventana estricta
# ============================================================================

[CmdletBinding()]
param(
    [string]$PgHost = '127.0.0.1',
    [int]$Port = 5432,
    [string]$User = 'postgres',
    [string]$Database = 'chronos_fire',
    [string]$Psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe',
    [string]$ReportDir = (Join-Path $PSScriptRoot '..\..\..\reports\validacion_6_8_20261114'),
    [int]$FromYear = 2013,
    [int]$ToYear = 2025,
    [string[]]$ViirsSensors = @('VIIRS_SNPP', 'VIIRS_NOAA20', 'VIIRS_NOAA21'),
    [int]$MaxHours = 24,
    [switch]$SkipBase,
    [switch]$SkipSweep,
    [switch]$Summarize
)

$ErrorActionPreference = 'Stop'

# CSV con punto decimal, independiente del locale de Windows.
$invariant = [System.Globalization.CultureInfo]::InvariantCulture
[System.Threading.Thread]::CurrentThread.CurrentCulture = $invariant
[System.Threading.Thread]::CurrentThread.CurrentUICulture = $invariant

if (-not (Test-Path $Psql)) {
    throw "No se encontro psql en: $Psql"
}

if (-not $env:PGPASSWORD) {
    throw 'Definir $env:PGPASSWORD antes de ejecutar.'
}

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

function Invoke-PsqlFile {
    param(
        [string]$Script,
        [string]$OutFile,
        [hashtable]$Vars = @{}
    )

    $psqlArgs = @(
        '-h', $PgHost,
        '-p', $Port,
        '-U', $User,
        '-d', $Database,
        '-v', 'ON_ERROR_STOP=1'
    )

    foreach ($key in $Vars.Keys) {
        $psqlArgs += @('-v', "$key=$($Vars[$key])")
    }

    $psqlArgs += @('-f', $Script, '-o', $OutFile)

    Write-Host "==> $(Split-Path $Script -Leaf) -> $(Split-Path $OutFile -Leaf)"
    & $Psql @psqlArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Fallo psql: $Script"
    }
}

if (-not $SkipBase) {
    $baseScripts = @(
        '00_contrato.sql',
        '01_interna.sql',
        '02_eventos.sql',
        '03_sensibilidad.sql',
        '03b_sensibilidad_high.sql'
    )

    foreach ($name in $baseScripts) {
        $script = Join-Path $PSScriptRoot $name
        $out = Join-Path $ReportDir ($name -replace '\.sql$', '.txt')
        Invoke-PsqlFile -Script $script -OutFile $out
    }
}

if (-not $SkipSweep) {
    foreach ($sensor in $ViirsSensors) {
        $startYear = switch ($sensor) {
            'VIIRS_SNPP'   { 2013 }
            'VIIRS_NOAA20' { 2018 }
            'VIIRS_NOAA21' { 2023 }
            default        { $FromYear }
        }

        $y0 = [Math]::Max($FromYear, $startYear)

        for ($year = $y0; $year -le $ToYear; $year++) {
            $out = Join-Path $ReportDir ("04_coincidencia_{0}_{1}.txt" -f $year, $sensor)
            Invoke-PsqlFile `
                -Script (Join-Path $PSScriptRoot '04_coincidencia.sql') `
                -OutFile $out `
                -Vars @{
                    anio = $year
                    viirs_sensor = $sensor
                    max_hours = $MaxHours
                }
        }
    }
}

if ($Summarize -or -not $SkipSweep) {
    $files = Get-ChildItem -Path $ReportDir -Filter '04_coincidencia_*.txt'
    $rows = foreach ($file in $files) {
        $is3h = $file.BaseName -match '_3h$'
        $match = [regex]::Match($file.BaseName, '^04_coincidencia_(\d{4})_(.+?)(?:_3h)?$')
        if (-not $match.Success) { continue }

        $year = [int]$match.Groups[1].Value
        $sensor = $match.Groups[2].Value.ToUpperInvariant()
        if ($sensor -eq 'SNPP') { $sensor = 'VIIRS_SNPP' }

        $line = Get-Content $file.FullName |
            Where-Object { $_ -match '^\|\s*\d+\s*\|' } |
            Select-Object -First 1
        if (-not $line) { continue }

        $v = $line.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
        if ($v.Count -lt 21) { continue }

        [pscustomobject]@{
            Year                          = $year
            Sensor                        = $sensor
            WindowHours                   = if ($is3h) { 3 } else { 24 }
            ModisTotal                    = [int64]$v[0]
            ViirsTotal                    = [int64]$v[1]
            ModisCoincidentes             = [int64]$v[2]
            ViirsCoincidentes             = [int64]$v[3]
            Pares1km                      = [int64]$v[4]
            PctModis                      = [double]$v[5]
            PctViirs                      = [double]$v[6]
            ParesPorModisCoincidente      = [double]$v[7]
            MedFrpModisMatch              = [double]$v[8]
            MedFrpViirsMatch              = [double]$v[9]
            MedDensModisMatch             = [double]$v[10]
            MedDensViirsMatch             = [double]$v[11]
            MedFrpModisMatched            = [double]$v[12]
            MedFrpModisUnmatched          = [double]$v[13]
            MedFrpViirsMatched            = [double]$v[14]
            MedFrpViirsUnmatched          = [double]$v[15]
            ParesLt500m                   = [int64]$v[16]
            Pares500m1km                  = [int64]$v[17]
            Pares1km1_5km                 = [int64]$v[18]
            ParesGe1_5km                  = [int64]$v[19]
            MedianaKmLt1km                = [double]$v[20]
        }
    }

    $summary = Join-Path $ReportDir '05_resumen_coincidencia.csv'
    $rows | Sort-Object Year, Sensor, WindowHours |
        Export-Csv -Path $summary -NoTypeInformation -Encoding UTF8

    Write-Host "Resumen: $summary ($($rows.Count) filas)"
}

