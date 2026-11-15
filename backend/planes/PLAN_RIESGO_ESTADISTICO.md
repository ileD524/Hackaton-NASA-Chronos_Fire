# Plan de inferencia estadística — Riesgo de fuego por celda

**Proyecto:** Hackaton NASA Chronos Fire
**Base de datos:** PostgreSQL 17, `chronos_fire`
**Stack:** Java 21, Spring Boot 3.3, JDBC (`JdbcTemplate`), Caffeine
**Cobertura:** Argentina, 2000–2026
**Estado:** propuesto. No implementado. Ninguna tabla ni endpoint de este plan existe todavía.

---

## 1. Objetivo

Agregar al producto una **estimación probabilística por renglón**, basada en los registros históricos, que hoy no existe. En concreto:

1. Para cada **celda 0,05° × semana ISO**, estimar `P(fuego en esa celda durante esa semana)` con su intervalo de incertidumbre y la cantidad de años de referencia.
2. Para cada **renglón de `/api/alerts`**, exponer esa probabilidad como contexto histórico y un **percentil de actividad** que responda "¿cuán inusual es lo observado frente a los 13 años de esa celda-semana?".
3. Para el **calendario/mapa**, reemplazar la anomalía de AOI (`observed / baseline`) por una comparación probabilística, sin romper los campos existentes.

### Fuera de alcance

- Predicción meteorológica o de propagación.
- Riesgo oficial, alerta oficial o calibración física de FRP.
- Cobertura real de nubosidad por píxel (se usa un proxy, ver §3.2).
- Aprendizaje automático con features (queda como fase 5 opcional).

> **Restricción de arquitectura vigente:** el backend es de **solo lectura** (`CONTRATO_API.md`, línea 5). Por eso todo el cálculo pesado se materializa con **scripts SQL aplicados a mano**, igual que `fix_calendar.sql` y `rebuild_calendar.sql`. El backend solo hace `SELECT`.

---

## 2. Estado de partida (medido el 2026-11-15)

Estos números salen de la base local, no de supuestos:

| Objeto | Medida |
|---|---|
| `detections` | 7.082.474 filas |
| `grid_daily` | 2.103.053 filas |
| `grid_weekly` | 1.575.089 filas, 70.172 celdas |
| `grid_weekly_sensor` | 2.265.477 filas, 70.172 celdas |
| `calendar_weekly` | 3.719.116 filas, 782.840 con tasa calculada |
| `coverage_daily` | **0 filas** (esquema correcto, sin datos) |
| Cobertura regional por sensor | ~365 días/año en todos los años (MODIS 360–366; SNPP 346–366; NOAA-20/21 similar) |
| Celdas con actividad (SNPP 2013–2025) | 55.561; **20.739** con ≥5 años; **9.550** con ≥10 |
| Celdas-semana con ≥5 años (SNPP) | **11.850 de 459.650 (2,6 %)**; mediana 1 año |
| Tasa global de día-con-fuego (celdas recurrentes) | 0,17; mediana 1,2 días de fuego por semana |

### 2.1 Consecuencia metodológica

La celda-semana es **dispersa** como unidad de "años con actividad" (mediana 1). Por eso el plan **no** modela la frecuencia condicionada a detección: modela el **evento semanal** (¿hubo fuego en esa celda esa semana?) sobre los **años con cobertura**, que son 13 para SNPP y 9 para MODIS. Así `n` es estable y la probabilidad es estimable en todas las celdas del AOI, incluidas las de `k = 0`.

La cobertura regional es casi completa (~365/365), lo que hace defendible el denominador "años observados". La nubosidad sigue siendo un sesgo no medido y se documenta como cota inferior (§9).

---

## 3. Decisiones metodológicas

Estas decisiones deben quedar cerradas antes de escribir código. Cambiarlas después obliga a reconstruir tablas.

### 3.1 Unidad y modelo

```text
k  = años de referencia en que la celda tuvo >= 1 fire_day en la semana ISO W
n  = años de referencia con cobertura para esa semana ISO
p  = (k + alpha) / (n + alpha + beta)          [media posterior Beta-Binomial]
```

Interpretación expuesta al usuario: *"frecuencia histórica estimada de que esta celda tenga actividad de fuego en esta semana del año"*.

### 3.2 Denominador: cobertura, no detección

`n` sale de `coverage_daily` (`has_data = true`), no de "años en que hubo detección". Esto corrige el sesgo de `fire_day_rate_given_detection`, cuyo denominador son días con detección y por eso satura cerca de 1.

`coverage_type = 'proxy'`: se marca un día como cubierto si el sensor registró al menos una detección en la región. No prueba ausencia de nubes, pero prueba que el satélite observó y que el pipeline tenía ese día. Es la única señal disponible hoy y deja la puerta abierta a reemplazarla por cobertura real (`coverage_type = 'observed'`) sin cambiar el esquema.

### 3.3 Prior empírico por semana ISO

Sin suavizado, una celda con `k = 0` queda en `p = 0` exacto y el mapa afirma certeza que no tiene.

```text
mu_w    = tasa AOI de la semana W = (celdas-año con fuego) / (celdas-año con cobertura)
kappa   = 8                                  [fuerza del prior, débil]
alpha   = mu_w * kappa
beta    = (1 - mu_w) * kappa
```

`mu_w` captura la estacionalidad (semana 40 ≠ semana 20) y `kappa = 8` deja que el dato local domine apenas hay 3–4 años con fuego.

### 3.4 Incertidumbre

**Intervalo de Wilson al 90 %** sobre la proporción cruda `k/n`, con `z = 1,6449`. Es aritmética cerrada: no requiere extensiones de PostgreSQL ni `commons-math3`. Con `n = 13` el intervalo es ancho a propósito (p. ej. `p = 0,30 → [0,12; 0,57]`), y eso se muestra, no se esconde.

El intervalo se calcula sobre `k/n`; `p_fire` es la media posterior suavizada. Ambos se guardan y se documenta la diferencia.

### 3.5 Era del sensor

| Era | Años | Uso |
|---|---|---|
| `MODIS` | 2003–2011 | años previos a VIIRS |
| `SNPP` | 2013–2025 | era principal; `AUTO` para ≥2013 |

Se replica `CalendarService.resolvePlan`: `AUTO` → `SNPP` si `year >= 2013`, si no `MODIS`. **No se mezclan eras** y no se construyen tablas de riesgo para NOAA-20/21 en el MVP (historia demasiado corta: 2019–2025 y 2024–2025).

### 3.6 Confiabilidad

| `reliability` | Condición |
|---|---|
| `high` | `ref_years >= 10` |
| `low` | `5 <= ref_years < 10` |
| `insufficient` | `ref_years < 5` o sin cobertura |

Regla de producto: **nunca** mostrar `p` sin `ref_years` y sin intervalo. Si `insufficient`, la UI muestra `—`.

### 3.7 Lo que no se toca

- `anomaly`, `baselineFireCells` y `confidence` siguen existiendo con su semántica actual (compatibilidad de contrato y de la validación §6.8).
- Los campos nuevos son **aditivos y nullable**. Ningún endpoint cambia de forma.
- `fire_day_rate_given_detection` queda como diagnóstico, no como probabilidad.

---

## 4. Cambios en la base de datos

### 4.1 Resumen

| Objeto | Acción | Motivo |
|---|---|---|
| `detections` | **ninguna** | no tocar el dato canónico |
| `grid_daily` | **ninguna** | calendario y heatmap dependen de ella |
| `grid_weekly` | **ninguna** | usado por `CalendarRepository` |
| `grid_weekly_sensor` | **ninguna** | base del baseline SNPP |
| `calendar_weekly` | **ninguna** | contrato y validación vigentes |
| `coverage_daily` | **poblar** (la tabla ya existe) | denominador auditable |
| `risk_cell_week` | **crear** | riesgo materializado por celda-semana |
| `risk_prior_week` | **crear** | `mu_w`, `alpha`, `beta` por era y semana |
| `risk_backtest` | **crear** | calibración publicada (fase 4) |
| índice `grid_weekly_sensor (grid_id, week_of_year)` | **crear** | joins por celda en alertas |

**No hace falta PostGIS, ni extensiones, ni Flyway.** El proyecto aplica SQL a mano; se sigue ese patrón.

### 4.2 Script `backend/db/migraciones/populate_coverage.sql`

Idempotente. Se corre una vez después de cada carga del ETL.

```sql
BEGIN;

INSERT INTO coverage_daily (region, sensor, source, acq_date, has_data, coverage_type)
SELECT 'AR', d.sensor, d.source, d.acq_date, true, 'proxy'
FROM (SELECT DISTINCT sensor, source, acq_date FROM detections) d
ON CONFLICT (region, sensor, source, acq_date) DO NOTHING;

COMMIT;

-- Verificación: debe dar ~365 por año y sensor
-- SELECT sensor, extract(year FROM acq_date) AS anio, count(*) FILTER (WHERE has_data)
-- FROM coverage_daily GROUP BY 1,2 ORDER BY 1,2;
```

### 4.3 Script `backend/db/migraciones/add_risk_layer.sql`

Solo DDL idempotente. No toca tablas existentes.

```sql
BEGIN;

CREATE TABLE IF NOT EXISTS risk_prior_week (
    era           TEXT     NOT NULL,          -- 'MODIS' | 'SNPP'
    week_of_year  SMALLINT NOT NULL,
    mu            REAL     NOT NULL,          -- tasa AOI de la semana
    kappa         REAL     NOT NULL,
    alpha         REAL     NOT NULL,
    beta          REAL     NOT NULL,
    PRIMARY KEY (era, week_of_year)
);

CREATE TABLE IF NOT EXISTS risk_cell_week (
    grid_id       TEXT     NOT NULL,
    cell_lat      DOUBLE PRECISION NOT NULL,
    cell_lon      DOUBLE PRECISION NOT NULL,
    week_of_year  SMALLINT NOT NULL,
    era           TEXT     NOT NULL,
    ref_years     INTEGER  NOT NULL,          -- n
    fire_years    INTEGER  NOT NULL,          -- k
    fire_days     INTEGER  NOT NULL,          -- intensidad acumulada
    p_fire        REAL     NOT NULL,          -- media posterior
    p_lo          REAL     NOT NULL,          -- Wilson 90%
    p_hi          REAL     NOT NULL,
    det_p50       REAL,                       -- n_detections semanal, p50 histórico
    det_p90       REAL,                       -- n_detections semanal, p90 histórico
    reliability   TEXT     NOT NULL,          -- high | low | insufficient
    model_version TEXT     NOT NULL,
    built_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (grid_id, week_of_year, era)
);

CREATE INDEX IF NOT EXISTS risk_cell_week_week_idx
    ON risk_cell_week (week_of_year, era);
CREATE INDEX IF NOT EXISTS risk_cell_week_latlon_idx
    ON risk_cell_week (cell_lat, cell_lon);

CREATE TABLE IF NOT EXISTS risk_backtest (
    model_version  TEXT     NOT NULL,
    era            TEXT     NOT NULL,
    holdout_year   SMALLINT NOT NULL,
    brier          REAL,
    brier_baseline REAL,
    bss            REAL,
    auc            REAL,
    n              BIGINT,
    built_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (model_version, era, holdout_year)
);

CREATE INDEX IF NOT EXISTS grid_weekly_sensor_cell_week_idx
    ON grid_weekly_sensor (grid_id, week_of_year);

COMMIT;
```

Volumen esperado de `risk_cell_week`: 70.172 celdas × 53 semanas × 2 eras ≈ **7,4 M filas** (comparable a `calendar_weekly`).

### 4.4 Script `backend/db/rebuild_risk.sql`

Idempotente (`TRUNCATE` + `INSERT`), mismo patrón que `rebuild_calendar.sql`. Se corre después de `populate_coverage.sql`.

**Paso 1 — `risk_prior_week`**

```sql
TRUNCATE risk_prior_week;

WITH cov AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           extract(isoyear FROM acq_date)::smallint AS isoyear,
           extract(week    FROM acq_date)::smallint AS week_of_year
    FROM coverage_daily
    WHERE has_data
    GROUP BY 1,2,3
),
ref AS (
    SELECT era, week_of_year, count(*)::int AS n_ref
    FROM cov GROUP BY 1,2
),
cell_year AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           g.week_of_year,
           g.grid_id, g.isoyear,
           bool_or(g.fire_days > 0) AS fire_year
    FROM grid_weekly_sensor g
    WHERE (g.sensor = 'MODIS'      AND g.isoyear BETWEEN 2003 AND 2011)
       OR (g.sensor = 'VIIRS_SNPP' AND g.isoyear BETWEEN 2013 AND 2025)
    GROUP BY 1,2,3,4
),
agg AS (
    SELECT era, week_of_year,
           count(*)::numeric                        AS cell_years,
           count(*) FILTER (WHERE fire_year)::numeric AS fire_cell_years
    FROM cell_year GROUP BY 1,2
)
INSERT INTO risk_prior_week (era, week_of_year, mu, kappa, alpha, beta)
SELECT a.era, a.week_of_year,
       (a.fire_cell_years / NULLIF(a.cell_years, 0))::real AS mu,
       8::real                                              AS kappa,
       (8 * a.fire_cell_years / NULLIF(a.cell_years, 0))::real,
       (8 * (1 - a.fire_cell_years / NULLIF(a.cell_years, 0)))::real
FROM agg a
JOIN ref r ON r.era = a.era AND r.week_of_year = a.week_of_year;
```

**Paso 2 — `risk_cell_week`**

```sql
TRUNCATE risk_cell_week;

WITH grid AS (
    SELECT DISTINCT grid_id, cell_lat, cell_lon FROM grid_weekly
),
weeks AS (SELECT generate_series(1,53)::smallint AS week_of_year),
eras  AS (SELECT unnest(ARRAY['MODIS','SNPP'])::text AS era),
cov AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           extract(week FROM acq_date)::smallint AS week_of_year,
           count(DISTINCT extract(isoyear FROM acq_date))::int AS ref_years
    FROM coverage_daily
    WHERE has_data
    GROUP BY 1,2
),
cell AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           grid_id, week_of_year,
           count(*)::int                                AS n_years_activity,
           count(*) FILTER (WHERE fire_days > 0)::int   AS k,
           sum(fire_days)::int                          AS fire_days,
           percentile_cont(0.5) WITHIN GROUP (ORDER BY n_detections) AS det_p50,
           percentile_cont(0.9) WITHIN GROUP (ORDER BY n_detections) AS det_p90
    FROM grid_weekly_sensor
    WHERE (sensor = 'MODIS'      AND isoyear BETWEEN 2003 AND 2011)
       OR (sensor = 'VIIRS_SNPP' AND isoyear BETWEEN 2013 AND 2025)
    GROUP BY 1,2,3
),
base AS (
    SELECT g.grid_id, g.cell_lat, g.cell_lon, w.week_of_year, e.era,
           c.ref_years,
           coalesce(k.k, 0)               AS k,
           coalesce(k.fire_days, 0)       AS fire_days,
           k.det_p50, k.det_p90,
           p.alpha, p.beta
    FROM grid g
    CROSS JOIN weeks w
    CROSS JOIN eras e
    JOIN cov c ON c.era = e.era AND c.week_of_year = w.week_of_year
    JOIN risk_prior_week p ON p.era = e.era AND p.week_of_year = w.week_of_year
    LEFT JOIN cell k ON k.grid_id = g.grid_id
                    AND k.week_of_year = w.week_of_year
                    AND k.era = e.era
),
calc AS (
    SELECT b.*,
           (k::numeric / ref_years)                       AS phat,
           (k + alpha)::numeric / (ref_years + alpha + beta) AS p
    FROM base b
),
wilson AS (
    SELECT c.*,
           1.6448536269514722::numeric      AS z,
           power(1.6448536269514722::numeric, 2) AS z2
    FROM calc c
)
INSERT INTO risk_cell_week (
    grid_id, cell_lat, cell_lon, week_of_year, era,
    ref_years, fire_years, fire_days, p_fire, p_lo, p_hi,
    det_p50, det_p90, reliability, model_version)
SELECT grid_id, cell_lat, cell_lon, week_of_year, era,
       ref_years, k, fire_days,
       round(p::numeric, 4)::real,
       round(greatest(0, (
            (phat + z2/(2*ref_years))
          - z * sqrt(phat*(1-phat)/ref_years + z2/(4.0*ref_years*ref_years))
       ) / (1 + z2/ref_years))::numeric, 4)::real,
       round(least(1, (
            (phat + z2/(2*ref_years))
          + z * sqrt(phat*(1-phat)/ref_years + z2/(4.0*ref_years*ref_years))
       ) / (1 + z2/ref_years))::numeric, 4)::real,
       det_p50, det_p90,
       CASE WHEN ref_years >= 10 THEN 'high'
            WHEN ref_years >= 5  THEN 'low'
            ELSE 'insufficient' END,
       'risk-v1'
FROM wilson;

ANALYZE risk_cell_week;
```

**Aceptación del rebuild**

- `count(*)` entre 7,0 y 7,6 M; `count(DISTINCT era) = 2`.
- `p_fire BETWEEN 0 AND 1`, `p_lo <= p_fire <= p_hi` (o discrepancia explicada por el suavizado), `ref_years >= 1`.
- `ref_years = 13` para SNPP salvo semana 53 (12) y 9 para MODIS (2012 no aplica).
- Tiempo total < 5 min en la máquina de desarrollo.

---

## 5. Fases de implementación

### Fase 0 — Cobertura y parámetros (0,5 día)

| Tarea | Artefacto |
|---|---|
| Poblar `coverage_daily` | `db/migraciones/populate_coverage.sql` |
| Verificar huecos de cobertura | consulta en el mismo script |
| Construir `risk_prior_week` | `db/rebuild_risk.sql` paso 1 |
| Documentar el comando de aplicación | `backend/db/README.md` (nuevo) |

**Aceptación:** `count(*) > 200.000` en `coverage_daily`; ninguna semana con `n_ref = 0`; `mu` entre 0 y 1 en las 53 semanas.

---

### Fase 1 — Tabla de riesgo (1,5 días)

| Tarea | Artefacto |
|---|---|
| Construir `risk_cell_week` | `db/rebuild_risk.sql` paso 2 |
| Índice para alertas | `db/migraciones/add_risk_layer.sql` |
| Script de validación de la tabla | `db/validacion/06_riesgo.sql` (nuevo) |

**Aceptación:** las del §4.4, más `06_riesgo.sql` sin filas devueltas en los chequeos de rango.

---

### Fase 2 — API por renglón (1,5 días)

| Archivo | Cambio |
|---|---|
| `repository/RiskRepository.java` | **nuevo**: `byCellsWeek(...)`, `byCellRange(...)`, `prior(...)` |
| `service/RiskMath.java` | **nuevo**: Wilson, prior, percentil; sin dependencias externas |
| `service/RiskService.java` | **nuevo**: orquesta repos, `@Cacheable("risk")` |
| `dto/Alert.java` | + `fireProbability`, `probabilityLow`, `probabilityHigh`, `probabilityRefYears`, `probabilityReliability`, `activityPercentile` |
| `repository/AlertRepository.java` | agregar `grid_id` al `SELECT`/`GROUP BY` y `LEFT JOIN risk_cell_week` por semana ISO y era |
| `service/AlertService.java` | mapear campos nuevos; `level()` pasa a derivarse de `p` y percentil |
| `dto/HeatmapCell.java` | + los mismos campos de probabilidad |
| `repository/CalendarRepository.java` | `LEFT JOIN risk_cell_week` en `heatmap()`; reemplazar el `anomaly` hardcodeado en `null` (línea 286) |
| `openapi/chronosfire-api.yaml` | campos nuevos + fórmula y advertencias en `description` |

**Semántica de los campos**

| Campo | Definición |
|---|---|
| `fireProbability` | `p_fire` de la celda para la semana ISO consultada |
| `probabilityLow` / `probabilityHigh` | Wilson 90 % sobre `k/n` |
| `probabilityRefYears` | `n` (años de referencia con cobertura) |
| `probabilityReliability` | `high` / `low` / `insufficient` |
| `activityPercentile` | fracción de años de referencia con `n_detections` semanal menor o igual al observado. **Solo se calcula si `days = 7`**; en otro caso `null` (la unidad histórica es semanal y no se escala) |

**`level()` propuesto** (mantiene el enum `info | watch | warning` para no romper el frontend):

```text
warning : activityPercentile >= 0.90  o  fireProbability >= 0.50
watch   : activityPercentile >= 0.75  o  fireProbability >= 0.25
info    : resto
insufficient -> siempre info, con p en null
```

Los cortes son configurables y **deben reemplazarse por los cuantiles observados** después de la fase 4.

**Camino rápido sin DDL (4 h, opcional):** solo con el índice `grid_weekly_sensor (grid_id, week_of_year)` se puede calcular `fireProbability` y `activityPercentile` al vuelo para las 50 alertas, contra los 13 valores históricos de cada celda-semana. Sirve como demo sin crear tablas; no cubre calendario ni mapa.

**Aceptación:** `OpenApiContractTest` pasa; los endpoints existentes devuelven exactamente los mismos campos previos; con AOI Delta 2020 las celdas recurrentes muestran `p` con `n = 13`.

---

### Fase 3 — Calendario y mapa (1 día)

| Tarea | Detalle |
|---|---|
| Heatmap por celda | `fireProbability` del rango consultado: sumar `k` y `n` de las semanas del rango y recalcular `p` + Wilson |
| `anomaly` por celda | dejar de devolver `null`; usar `p_fire` vs `p` del rango (misma escala) |
| AOI-mes (opcional) | `P(X >= observed)` con Poisson-binomial aproximada por suma de `p` y suma de `p(1-p)`; expuesto como `tailProbability` en `MonthlyCell` |
| Frontend | `types.ts`, `AlertsPanel.vue` (badge `%` + `n`), `MethodologyPanel.vue` (fórmula y límites), rampa `risk` opcional en `useColorRamp.ts` |

**Aceptación:** el calendario no muestra probabilidades con `reliability = insufficient`; el texto de metodología menciona el proxy de cobertura y la cota inferior.

---

### Fase 4 — Backtesting y calibración (1,5 días)

| Tarea | Detalle |
|---|---|
| Holdout temporal | entrenar 2013–2024, evaluar 2025 (y repetir 2019–2024 → 2025 para estabilidad) |
| Métricas | Brier, Brier Skill Score vs `mu_w`, AUC, curva de fiabilidad por decil |
| Persistencia | `risk_backtest` |
| Publicación | `methodology` en `/api/meta` o sección estática en la UI |

**Puerta de calidad:** si `BSS < 0,05` o el decil superior se desvía más de 0,10 de la diagonal, la tabla se publica con `model_version = risk-v1-low` y la UI lo declara. No se promete calibración que no se midió.

---

### Fase 5 — Modelo con features (opcional, 3–5 días)

Solo si la fase 4 pasa. Regresión logística o GBM **offline** con: semana ISO, actividad de la vecindad 3×3, `fire_days` históricos, FRP histórico, `fire_type`, era. Se exportan coeficientes o scores precalculados; el backend sigue siendo de solo lectura y sin dependencias de ML.

---

## 6. Contrato de API

Cambios **aditivos y nullable**. Nada se renombra ni se elimina.

### `GET /api/alerts`

```json
{
  "cellLat": -32.15,
  "cellLon": -60.45,
  "nDetections": 14,
  "frpMaxMw": 38.2,
  "level": "warning",
  "fireProbability": 0.41,
  "probabilityLow": 0.18,
  "probabilityHigh": 0.68,
  "probabilityRefYears": 13,
  "probabilityReliability": "high",
  "activityPercentile": 0.92
}
```

### `GET /api/calendar/heatmap`

Cada celda suma los mismos campos de probabilidad del rango consultado.

### Documentación obligatoria en OpenAPI

- Fórmula: `p = (k + α) / (n + α + β)`, Wilson 90 %.
- `n` = años con cobertura (`coverage_type = 'proxy'`).
- Advertencia: "frecuencia histórica estimada, no predicción ni riesgo oficial".
- `activityPercentile` solo válido con `days = 7`.

---

## 7. Pruebas

| Test | Qué cubre |
|---|---|
| `RiskMathTest` | Wilson contra casos conocidos (`p=0,3`, `n=13` → `[0,12; 0,57]`); prior con `k=0`; monotonía; `k=n`; `n=0` |
| `RiskServiceTest` | mapeo de `reliability`; `activityPercentile` con 13 valores; `null` si `days != 7` |
| `AlertControllerTest` / `CalendarControllerTest` | stubs con campos nuevos; campos viejos intactos |
| `OpenApiContractTest` | los campos nuevos están documentados |
| `db/validacion/06_riesgo.sql` | `p` fuera de rango, `p_lo > p_fire`, `ref_years < 1`, celdas sin prior, era inválida |

**Invariantes de regresión** (deben dar idéntico antes/después):

```sql
SELECT count(*) FROM detections;        -- 7.082.474
SELECT count(*) FROM grid_daily;        -- 2.103.053
SELECT count(*) FROM calendar_weekly;   -- 3.719.116
```

---

## 8. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Proxy de cobertura ignora nubes | `p` subestimada en zonas nubosas | documentar como cota inferior; `coverage_type` permite reemplazo futuro |
| Cambio de era mal aplicado | serie no comparable | `era` obligatoria en la PK; `AUTO` replica `resolvePlan` |
| Tabla de 7,4 M filas | rebuild lento / dump grande | rebuild medido, `ANALYZE`, incluir en el dump de `docker/` |
| Interpretación como "riesgo oficial" | problema reputacional y de comunicación | texto fijo en UI/OpenAPI; `reliability` siempre visible |
| `activityPercentile` con ventana ≠ 7 días | comparación inválida | devolver `null` y documentarlo |
| Deriva de contrato | romper el frontend | campos aditivos + `OpenApiContractTest` |

---

## 9. Límites que se deben declarar

- **Cota inferior:** la cobertura es regional, no por celda; una celda nublada puede tener `fire_days = 0` sin estar libre de fuego.
- **Intervalos anchos:** con `n = 13`, `p = 0,3` tiene un IC de ~±0,22. Es la incertidumbre real.
- **Sin tendencia climática:** 2013–2025 pesa igual; la ponderación exponencial queda como mejora.
- **No es predicción:** no incorpora pronóstico meteorológico, combustible ni igniciones.
- **Sin recuento de incendios:** sigue siendo actividad de hotspots, no eventos únicos.

---

## 10. Cronograma

| Entrega | Fases | Esfuerzo | Resultado visible |
|---|---|---|---|
| Demo mínima | 2 (camino rápido) | 4 h | `%` histórico y percentil por alerta, sin DDL |
| MVP defendible | 0 + 1 + 2 | 3,5 días | riesgo materializado, API y UI |
| Completo | + 3 + 4 | 6 días | calendario/mapa por celda + calibración |
| Opcional | + 5 | +3–5 días | modelo con features |

---

## 11. Checklist de aceptación final

- [ ] `coverage_daily` poblada y auditada (~365 días/año/sensor).
- [ ] `risk_prior_week` con 53 semanas por era y `mu` válido.
- [ ] `risk_cell_week` construida, con rangos e intervalos consistentes.
- [ ] `rebuild_risk.sql` idempotente y documentado en `backend/db/README.md`.
- [ ] `RiskMathTest` y `RiskServiceTest` en verde.
- [ ] `OpenApiContractTest` en verde; contratos previos sin cambios.
- [ ] Conteos de `detections`, `grid_daily` y `calendar_weekly` idénticos.
- [ ] `docker/` incluye las tablas nuevas en el dump.
- [ ] `docs/DECISIONES.md` con la sección "Probabilidad por celda-semana".
- [ ] UI muestra `p`, intervalo y `n`; nunca `p` solo.
- [ ] `risk_backtest` publicado con Brier y BSS del holdout.

---

## 12. Archivos a crear o modificar

### Nuevos

```text
backend/db/migraciones/populate_coverage.sql
backend/db/migraciones/add_risk_layer.sql
backend/db/rebuild_risk.sql
backend/db/validacion/06_riesgo.sql
backend/db/README.md
backend/src/main/java/com/chronosfire/backend/repository/RiskRepository.java
backend/src/main/java/com/chronosfire/backend/service/RiskMath.java
backend/src/main/java/com/chronosfire/backend/service/RiskService.java
backend/src/test/java/com/chronosfire/backend/service/RiskMathTest.java
backend/src/test/java/com/chronosfire/backend/service/RiskServiceTest.java
```

### Modificados

```text
backend/src/main/java/com/chronosfire/backend/dto/Alert.java
backend/src/main/java/com/chronosfire/backend/dto/HeatmapCell.java
backend/src/main/java/com/chronosfire/backend/repository/AlertRepository.java
backend/src/main/java/com/chronosfire/backend/repository/CalendarRepository.java
backend/src/main/java/com/chronosfire/backend/service/AlertService.java
backend/src/main/java/com/chronosfire/backend/service/CalendarService.java
backend/openapi/chronosfire-api.yaml
backend/src/test/java/com/chronosfire/backend/openapi/OpenApiContractTest.java
frontend/ChronosFire/src/data/types.ts
frontend/ChronosFire/src/components/AlertsPanel.vue
frontend/ChronosFire/src/components/MethodologyPanel.vue
docs/DECISIONES.md
```

### No se modifica

```text
backend/db/ChronosFireDB.sql          (el esquema oficial no cambia)
backend/db/rebuild_calendar.sql       (queda como está)
backend/db/migraciones/fix_calendar.sql
backend/CONTRATO_API.md               (se amplía en la sección de campos, sin cambiar la restricción de solo lectura)
```
