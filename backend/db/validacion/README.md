# Validacion §6.8 - Scripts reproducibles

Scripts de la validacion de armonizacion (PLAN_ARMONIZACION.md, §6.8).
Base: `chronos_fire` (PostgreSQL 17).

## Orden de ejecucion

| Script | Fase | Escribe en la base | Proposito |
|---|---|---|---|
| `00_contrato.sql` | V0 | Si (crea `validation_aoi` y `validation_aoi_window`) | Congelar AOIs, sensores y ventanas de referencia |
| `01_interna.sql` | V1 | No | Fuentes, cobertura, duplicados, solapamiento, consistencia |
| `02_eventos.sql` | V2 | No | Observado, baseline, `n_ref_years`, anomalia y confianza por evento |
| `03_sensibilidad.sql` | V3 | Solo temp table | MODIS vs SNPP vs todos los VIIRS; ranking y top-k |
| `03b_sensibilidad_high.sql` | V3b | Solo temp table | Sensibilidad con confianza `high` only |
| `04_coincidencia.sql` | V4 | No | Coincidencia espacial MODIS-VIIRS por anio |
| `05_smoke_delta_2020.sql` | Smoke | No | Delta 2020 semana 40 = 66/10/12/6,60 |
| `06_riesgo.sql` | R | No | Capa de riesgo estadistico: cobertura, prior, rangos y claves |

## Comandos

```powershell
$env:PGPASSWORD = 'postgres'
$psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
$db = 'chronos_fire'

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\00_contrato.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\01_interna.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\02_eventos.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\03_sensibilidad.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\03b_sensibilidad_high.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\05_smoke_delta_2020.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\06_riesgo.sql
```

V4 se corre por anio y por sensor VIIRS:

```powershell
& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -v anio=2020 -v viirs_sensor=VIIRS_SNPP -v max_hours=24 `
  -f backend\db\validacion\04_coincidencia.sql
```

Para el barrido completo hay un runner:

```powershell
$env:PGPASSWORD = 'postgres'

# Solo V4, SNPP 2013-2025
.\backend\db\validacion\run_validacion.ps1 -SkipBase -ViirsSensors VIIRS_SNPP

# Barrido completo (SNPP + NOAA-20 + NOAA-21)
.\backend\db\validacion\run_validacion.ps1 -SkipBase

# Solo consolidar los resultados existentes
.\backend\db\validacion\run_validacion.ps1 -SkipBase -SkipSweep -Summarize
```

El runner genera `05_resumen_coincidencia.csv`.

Variables de V4:

- `anio`: 2013..2025 (2026 es NRT parcial).
- `viirs_sensor`: `VIIRS_SNPP`, `VIIRS_NOAA20` o `VIIRS_NOAA21`.
- `max_hours`: 24 = mismo dia; 3 = ventana horaria estricta.

## AOIs

`00_contrato.sql` carga:

| AOI | Estado | Observado | Baseline | Referencia |
|---|---|---|---|---|
| `delta_2020` | confirmed | VIIRS_SNPP | VIIRS_SNPP | 2013-2025 |
| `corrientes_2022` | provisional | VIIRS_SNPP | VIIRS_SNPP | 2013-2025 |
| `salta_2013` | provisional | VIIRS_SNPP | VIIRS_SNPP | 2013-2025 |
| `cordoba_2013` | provisional | VIIRS_SNPP | VIIRS_SNPP | 2013-2025 |
| `la_pampa_2016_2017` | provisional | VIIRS_SNPP | VIIRS_SNPP | 2013-2025 |

Las AOIs `provisional` usan un bbox preliminar. Antes de citar sus resultados
hay que validar el bbox contra el evento documentado y cambiar `status` a
`confirmed`.

Para corregir una AOI, editar el `INSERT ... ON CONFLICT DO UPDATE` de
`00_contrato.sql` y volver a correrlo. Es idempotente.

## Reglas de calculo

- `observed` = `count(distinct grid_id)` con `fire_cells = 1` en la AOI/semana.
- `baseline` = mediana de `observed` en los anios de referencia, leave-one-out.
- `observed` y `baseline` usan el mismo sensor.
- `n_ref_years >= 7` para mostrar; `>= 10` y `baseline >= 10` para `high`.
- `observed >= 3` y `baseline >= 3`; si no, `anomaly = NULL`.
- Semana 53 se reporta aparte: tiene pocos anios de referencia.

## Salidas

Los resultados de la corrida del 2026-11-14 estan en:

```text
reports/validacion_6_8_20261114/
├─ 01_interna.txt
├─ 02_eventos.txt
├─ 03_sensibilidad.txt
├─ 03b_sensibilidad_high.txt
├─ 04_coincidencia_<anio>_<sensor>.txt
├─ 04_coincidencia_2020_snpp_3h.txt
└─ 05_resumen_coincidencia.csv
```

El informe consolidado esta en `backend/VALIDACION_6_8.md`.

## Notas

- `00_contrato.sql` es el unico script que crea objetos. El resto es lectura o
  usa una tabla temporal de sesion.
- Re-ejecutar V1-V4 despues de cada recarga del ETL y actualizar el informe.
- `coverage_daily` se llena con `db/migraciones/populate_coverage.sql`
  (proxy regional: dia con al menos una deteccion). Ver `db/README.md`.
