# Base de datos - scripts de riesgo estadistico

Scripts de las fases 0 y 1 de `backend/planes/PLAN_RIESGO_ESTADISTICO.md`.
Base: `chronos_fire` (PostgreSQL 17). El backend sigue siendo de solo lectura:
todo el calculo pesado se materializa aca.

## Orden de ejecucion

| # | Script | Fase | Escribe en la base | Proposito |
|---|---|---|---|---|
| 1 | `migraciones/add_risk_layer.sql` | 0-1 | Si (DDL) | Crea `risk_prior_week`, `risk_cell_week`, `risk_backtest` y el indice de alertas |
| 2 | `migraciones/populate_coverage.sql` | 0 | Si (`coverage_daily`) | Denominador auditable: dias con datos por sensor |
| 3 | `rebuild_risk.sql` | 0-1 | Si (`risk_*`) | Calcula prior y tabla de riesgo (7,4 M filas) |
| 4 | `validacion/06_riesgo.sql` | 1 | No | Validacion de rangos, claves y consistencia |
| 5 | `rebuild_risk_backtest.sql` | 4 | Si (`risk_backtest`) | Holdout 2025: Brier, BSS, AUC y curva por decil |

`add_risk_layer.sql` y `rebuild_risk.sql` son idempotentes. Se corre el 2 y el 3
despues de cada carga del ETL.

## Comandos

Desde la raiz del repo (`Hackaton-NASA-Chronos_Fire`), en PowerShell:

```powershell
$env:PGPASSWORD = 'postgres'
$psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
$db = 'chronos_fire'

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\migraciones\add_risk_layer.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\migraciones\populate_coverage.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\rebuild_risk.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\06_riesgo.sql

# Fase 4: holdout principal 2013-2024 -> 2025
& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -v model_version=risk-v1 `
  -f backend\db\rebuild_risk_backtest.sql

# Fase 4: control de estabilidad 2019-2024 -> 2025
& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -v model_version=risk-v1-t2019-2024 -v train_from=2019 -v train_to=2024 `
  -f backend\db\rebuild_risk_backtest.sql
```

El orden importa: si `coverage_daily` esta vacia, `rebuild_risk.sql` aborta con
un mensaje explicito.

## Resultados medidos (2026-11-15)

### Cobertura (`coverage_daily`)

- **19.260 filas** (8 combinaciones sensor/source, ver desvio 1).
- Dias cubiertos por anio dentro de las eras usadas:
  - MODIS 2003-2011: 99,7-100 %.
  - SNPP 2013-2025: 94,5-100 % (2022: 95,9 %; 2024: 94,5 %).
- **Hueco real:** SNPP 2022 semana 31 no tiene dias cargados. Esa semana queda
  con `ref_years = 12` en vez de 13. No hay ninguna semana con `n_ref = 0`.
- Semana 53: `n_ref = 2` en ambas eras (solo 2004/2009 y 2015/2020 tienen
  semana ISO 53). Por eso su `reliability` es `insufficient` y la UI debe
  mostrar `—`.

### Prior (`risk_prior_week`)

- 106 filas = 53 semanas x 2 eras.
- `mu` entre **0,0014 y 0,0397**; `alpha` entre 0,011 y 0,32; `beta` entre
  7,68 y 7,99.

### Tabla de riesgo (`risk_cell_week`)

- **7.438.232 filas** = 70.172 celdas x 53 semanas x 2 eras.
- `ref_years`: MODIS 9 en semanas 1-52 y 2 en la 53; SNPP 13 (12 en la semana
  31 de 2022) y 2 en la 53.
- `reliability`: 3.648.944 `high` + 3.648.944 `low` + 140.344
  `insufficient` (las dos semanas 53).
- 229.352 celdas-semana MODIS y 436.164 SNPP con `fire_years > 0`.
- `p_fire` en rango, `p_lo <= p_hi`, `fire_years <= ref_years` en el 100 % de
  las filas.

### Tiempos

| Paso | Tiempo |
|---|---|
| `risk_prior_week` | ~25 s |
| `risk_cell_week` (INSERT) | ~8 min 54 s |
| PK + indices (bulk) | ~1 min 12 s |
| Rebuild total | **~10 min 30 s** |
| `06_riesgo.sql` | ~40 s |

La primera version mantenia PK e indices durante el INSERT y tardaba
14 min 30 s. Se sueltan antes del INSERT y se reconstruyen despues, dentro de
la misma transaccion. **El objetivo del plan (<5 min) no se cumple en esta
maquina**; queda medido, no supuesto.

### Validacion

`06_riesgo.sql` devuelve **0 filas** en R1-R9 y en R13. R14 (resumen de fallas)
da todo en 0. R12 confirma que no se tocaron las tablas canonicas:
`detections` 7.082.474, `grid_daily` 2.103.053, `calendar_weekly` 3.719.116.

R11 es informativo: 1.506 filas (0,02 %) tienen `p_fire` fuera del intervalo de
Wilson sobre `k/n`, por el suavizado del prior (con `k = 0` el prior empuja
`p_fire` por encima de `p_lo = 0`; con `k = n`, por debajo de `p_lo`). Es
esperado y no es un error de datos.

### Backtesting (fase 4)

El backtest no usa `risk_cell_week` para predecir: recalcula `k`, `n` y el
prior solo con la ventana de entrenamiento y compara contra `fire_days > 0`
del holdout.

| Entrenamiento | Holdout | Brier | Brier base | BSS | AUC | Celdas-semana |
|---|---|---|---|---|---|---|
| 2013-2024 | 2025 | 0,011480 | 0,011818 | **0,0286** | 0,740 | 3.719.116 |
| 2019-2024 | 2025 | 0,011543 | 0,011823 | **0,0236** | 0,713 | 3.719.116 |

Curva de fiabilidad (2013-2024 -> 2025): el decil superior tiene p media 0,0852
y observado 0,0604 (desvio 0,0249); los deciles 1-9 quedan entre -0,0033 y
+0,0078.

**Puerta de calidad:** `BSS = 0,0286 < 0,05` -> la tabla se publica como
`risk-v1-low`. `risk_cell_week.model_version` se actualizo a `risk-v1-low` y
`rebuild_risk.sql` ya escribe esa etiqueta. El desvio del decil superior
(0,0249) si cumple el limite de 0,10. No se promete calibracion.

`/api/meta` publica el bloque `risk` con `modelVersion`, `calibrationStatus`,
`brier`, `bss`, `auc`, `cellsWeek`, `stabilityBss` y la nota del gate; la UI lo
muestra en el panel de metodologia.

## Desvios respecto del plan

El plan es la especificacion; estos puntos se corrigieron porque la SQL de
ejemplo daba resultados incorrectos. Quedan documentados para no repetirlos.

1. **Volumen de cobertura.** El criterio "`count(*) > 200.000`" es
   incompatible con la PK `(region, sensor, source, acq_date)`: hay 8
   combinaciones sensor/source y ~2.400 dias cada una, o sea 19.260 filas.
   La aceptacion correcta es "~365 dias por anio y sensor" (C1.2), que si se
   cumple.
2. **Denominador del prior.** El paso 1 del plan calculaba `cell_years` sobre
   `grid_weekly_sensor`, que solo tiene filas con detecciones. Ese denominador
   reproduce el sesgo "dado que hubo deteccion" y da `mu` entre 0,79 y 0,98
   (un prior que afirma fuego). Aca `mu = celdas-anio con fuego / (celdas del
   AOI x anios con cobertura)`, como dice §3.3, y da 0,0014-0,0397.
3. **Ventana de eras en el denominador.** Sin restringir `coverage_daily` a la
   ventana de cada era, MODIS tendria `n = 27` (2000-2026) y SNPP `n = 15`
   (2012-2026, con 2026 parcial). Se restringe a 2003-2011 y 2013-2025, igual
   que `grid_weekly_sensor`.
4. **Semana 53.** El plan esperaba `n = 12`; el calendario ISO solo da 2 anios
   con semana 53 en cada ventana. Se deja `insufficient`, que es el
   comportamiento correcto del producto.
5. **Hueco SNPP 2022-31.** No estaba previsto. No invalida la tabla, pero la
   semana tiene un anio de referencia menos y asi se reporta.
6. **Tiempo de rebuild.** 10,5 min medidos contra <5 min del plan.

## Limites (heredados del plan)

- La cobertura es regional, no por celda: `p_fire` es una **cota inferior**.
- `p_fire` es frecuencia historica estimada, **no prediccion ni riesgo
  oficial**.
- Nunca mostrar `p_fire` sin `ref_years`, sin `p_lo`/`p_hi` y sin
  `reliability`.

## Rollback

```sql
DROP TABLE IF EXISTS risk_cell_week;
DROP TABLE IF EXISTS risk_prior_week;
DROP TABLE IF EXISTS risk_backtest;
DROP INDEX IF EXISTS grid_weekly_sensor_cell_week_idx;
-- Opcional: TRUNCATE coverage_daily;
```

`coverage_daily` ya existia antes de este plan (estaba vacia); no se elimina.
