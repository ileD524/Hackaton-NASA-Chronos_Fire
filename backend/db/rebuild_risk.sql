-- ============================================================================
-- Chronos Fire - Rebuild de la capa de riesgo estadistico
-- Fases 0 y 1 de PLAN_RIESGO_ESTADISTICO.md
-- Base: chronos_fire (PostgreSQL 17)
--
-- Requiere, en este orden:
--   1. backend/db/migraciones/add_risk_layer.sql   (crea las tablas)
--   2. backend/db/migraciones/populate_coverage.sql (llena la cobertura)
--
-- Idempotente: TRUNCATE + INSERT, mismo patron que rebuild_calendar.sql.
-- Se corre despues de cada carga del ETL y de populate_coverage.sql.
-- Solo escribe en risk_prior_week y risk_cell_week. No toca tablas canonicas.
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

BEGIN;

SET LOCAL work_mem = '256MB';
SET LOCAL maintenance_work_mem = '512MB';

-- ----------------------------------------------------------------------------
-- 0. Guardas: tablas y cobertura
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.risk_prior_week') IS NULL
       OR to_regclass('public.risk_cell_week') IS NULL THEN
        RAISE EXCEPTION 'Faltan las tablas de riesgo. Ejecutar primero migraciones/add_risk_layer.sql';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM coverage_daily WHERE has_data) THEN
        RAISE EXCEPTION 'coverage_daily esta vacia. Ejecutar primero migraciones/populate_coverage.sql';
    END IF;
END $$;

-- ============================================================================
-- PASO 1 - risk_prior_week (Fase 0)
--
-- mu_w = celdas-anio con fuego / celdas-anio con cobertura
--
-- Desvio respecto del boceto del plan (§4.4, paso 1): el plan calculaba
-- cell_years sobre grid_weekly_sensor, que solo tiene filas con detecciones.
-- Ese denominador reproduce el sesgo de "dado que hubo deteccion" y da
-- mu ~0,79-0,98. Aca el denominador es n_cells * n_ref (cobertura regional),
-- que da mu ~0,003-0,034 y deja el prior donde corresponde. Ver
-- backend/db/README.md, seccion "Desvios respecto del plan".
-- ============================================================================
TRUNCATE risk_prior_week;

WITH cov AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           extract(isoyear FROM acq_date)::int       AS isoyear,
           extract(week    FROM acq_date)::smallint  AS week_of_year
    FROM coverage_daily
    WHERE has_data
      AND (
            (sensor = 'MODIS'      AND extract(isoyear FROM acq_date) BETWEEN 2003 AND 2011)
         OR (sensor = 'VIIRS_SNPP' AND extract(isoyear FROM acq_date) BETWEEN 2013 AND 2025)
      )
    GROUP BY 1, 2, 3
),
ref AS (
    SELECT era, week_of_year, count(*)::numeric AS n_ref
    FROM cov
    GROUP BY 1, 2
),
n_cells AS (
    SELECT count(DISTINCT grid_id)::numeric AS n_cells
    FROM grid_weekly
),
fire AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           week_of_year,
           count(DISTINCT (grid_id, isoyear))
               FILTER (WHERE fire_days > 0) AS fire_cell_years
    FROM grid_weekly_sensor
    WHERE (sensor = 'MODIS'      AND isoyear BETWEEN 2003 AND 2011)
       OR (sensor = 'VIIRS_SNPP' AND isoyear BETWEEN 2013 AND 2025)
    GROUP BY 1, 2
),
base AS (
    SELECT r.era, r.week_of_year,
           coalesce(f.fire_cell_years, 0)::numeric AS fire_cell_years,
           nc.n_cells * r.n_ref           AS cell_years,
           r.n_ref
    FROM ref r
    CROSS JOIN n_cells nc
    LEFT JOIN fire f ON f.era = r.era AND f.week_of_year = r.week_of_year
),
mu_calc AS (
    SELECT era, week_of_year, n_ref,
           (fire_cell_years / NULLIF(cell_years, 0))::real AS mu
    FROM base
)
INSERT INTO risk_prior_week (era, week_of_year, mu, kappa, alpha, beta)
SELECT era, week_of_year, mu,
       8::real                AS kappa,
       (8 * mu)::real         AS alpha,
       (8 * (1 - mu))::real   AS beta
FROM mu_calc;

\echo ''
\echo '=== P1.1 risk_prior_week: filas y rango de mu (esperado 106 filas, mu en [0,1]) ==='
SELECT count(*)      AS filas,
       count(DISTINCT era) AS eras,
       min(mu)       AS mu_min,
       max(mu)       AS mu_max,
       min(alpha)    AS alpha_min,
       min(beta)     AS beta_min
FROM risk_prior_week;

\echo ''
\echo '=== P1.2 Prior por era: 53 semanas completas y n_ref usado ==='
SELECT p.era,
       count(*)                    AS semanas,
       min(p.mu)                   AS mu_min,
       max(p.mu)                   AS mu_max,
       min(r.n_ref)                AS n_ref_min,
       max(r.n_ref)                AS n_ref_max
FROM risk_prior_week p
JOIN (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           extract(week FROM acq_date)::smallint AS week_of_year,
           count(DISTINCT extract(isoyear FROM acq_date))::numeric AS n_ref
    FROM coverage_daily
    WHERE has_data
      AND (
            (sensor = 'MODIS'      AND extract(isoyear FROM acq_date) BETWEEN 2003 AND 2011)
         OR (sensor = 'VIIRS_SNPP' AND extract(isoyear FROM acq_date) BETWEEN 2013 AND 2025)
      )
    GROUP BY 1, 2
) r USING (era, week_of_year)
GROUP BY p.era
ORDER BY p.era;

-- ============================================================================
-- PASO 2 - risk_cell_week (Fase 1)
--
-- k = anios de referencia con fire_days > 0 en la celda-semana
-- n = anios de referencia con cobertura en la semana ISO
-- p = (k + alpha) / (n + alpha + beta)
-- Wilson 90% sobre k/n.
--
-- Optimizacion: se sueltan PK e indices antes del INSERT y se reconstruyen
-- despues, dentro de la misma transaccion. Mantener 4 btree fila a fila
-- triplicaba el tiempo del rebuild. Al ser DDL transaccional, ningun lector
-- ve la tabla sin indices.
-- ============================================================================
TRUNCATE risk_cell_week;

DROP INDEX IF EXISTS risk_cell_week_week_idx;
DROP INDEX IF EXISTS risk_cell_week_latlon_idx;
ALTER TABLE risk_cell_week DROP CONSTRAINT IF EXISTS risk_cell_week_pkey;

WITH grid AS (
    SELECT DISTINCT grid_id, cell_lat, cell_lon
    FROM grid_weekly
),
weeks AS (
    SELECT generate_series(1, 53)::smallint AS week_of_year
),
eras AS (
    SELECT unnest(ARRAY['MODIS', 'SNPP'])::text AS era
),
cov AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           extract(week FROM acq_date)::smallint AS week_of_year,
           count(DISTINCT extract(isoyear FROM acq_date))::int AS ref_years
    FROM coverage_daily
    WHERE has_data
      AND (
            (sensor = 'MODIS'      AND extract(isoyear FROM acq_date) BETWEEN 2003 AND 2011)
         OR (sensor = 'VIIRS_SNPP' AND extract(isoyear FROM acq_date) BETWEEN 2013 AND 2025)
      )
    GROUP BY 1, 2
),
cell AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           grid_id, week_of_year,
           count(*) FILTER (WHERE fire_days > 0)::int AS k,
           sum(fire_days)::int                       AS fire_days,
           percentile_cont(0.5) WITHIN GROUP (ORDER BY n_detections) AS det_p50,
           percentile_cont(0.9) WITHIN GROUP (ORDER BY n_detections) AS det_p90
    FROM grid_weekly_sensor
    WHERE (sensor = 'MODIS'      AND isoyear BETWEEN 2003 AND 2011)
       OR (sensor = 'VIIRS_SNPP' AND isoyear BETWEEN 2013 AND 2025)
    GROUP BY 1, 2, 3
),
base AS (
    SELECT g.grid_id, g.cell_lat, g.cell_lon, w.week_of_year, e.era,
           c.ref_years,
           coalesce(k.k, 0)          AS k,
           coalesce(k.fire_days, 0)  AS fire_days,
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
           (k::numeric / ref_years)                          AS phat,
           (k + alpha)::numeric / (ref_years + alpha + beta) AS p
    FROM base b
)
INSERT INTO risk_cell_week (
    grid_id, cell_lat, cell_lon, week_of_year, era,
    ref_years, fire_years, fire_days, p_fire, p_lo, p_hi,
    det_p50, det_p90, reliability, model_version)
SELECT grid_id, cell_lat, cell_lon, week_of_year, era,
       ref_years, k, fire_days,
       round(p::numeric, 4)::real,
       round(greatest(0, (
            (phat + z2 / (2 * ref_years))
          - z * sqrt(phat * (1 - phat) / ref_years + z2 / (4.0 * ref_years * ref_years))
       ) / (1 + z2 / ref_years))::numeric, 4)::real,
       round(least(1, (
            (phat + z2 / (2 * ref_years))
          + z * sqrt(phat * (1 - phat) / ref_years + z2 / (4.0 * ref_years * ref_years))
       ) / (1 + z2 / ref_years))::numeric, 4)::real,
       det_p50::real, det_p90::real,
       CASE WHEN ref_years >= 10 THEN 'high'
            WHEN ref_years >= 5  THEN 'low'
            ELSE 'insufficient' END,
       'risk-v1'
FROM calc
CROSS JOIN (SELECT 1.6448536269514722::numeric AS z,
                   power(1.6448536269514722::numeric, 2) AS z2) w;

-- Reconstruccion de claves e indices (bulk build, mucho mas rapido que fila a fila)
ALTER TABLE risk_cell_week
    ADD CONSTRAINT risk_cell_week_pkey PRIMARY KEY (grid_id, week_of_year, era);

CREATE INDEX IF NOT EXISTS risk_cell_week_week_idx
    ON risk_cell_week (week_of_year, era);
CREATE INDEX IF NOT EXISTS risk_cell_week_latlon_idx
    ON risk_cell_week (cell_lat, cell_lon);

ANALYZE risk_cell_week;
ANALYZE risk_prior_week;

COMMIT;

-- ============================================================================
-- Aceptacion (PLAN_RIESGO_ESTADISTICO.md §4.4)
-- ============================================================================
\echo ''
\echo '=== A1. Volumen (esperado 7,0-7,6 M filas, 2 eras) ==='
SELECT count(*) AS filas,
       count(DISTINCT era) AS eras,
       count(DISTINCT grid_id) AS celdas
FROM risk_cell_week;

\echo ''
\echo '=== A2. Anios de referencia por era y semana (esperado 9/13; semana 53 = 2) ==='
SELECT era,
       min(ref_years) AS n_min,
       max(ref_years) AS n_max,
       count(*) FILTER (WHERE week_of_year = 53) AS filas_semana_53,
       min(ref_years) FILTER (WHERE week_of_year = 53) AS n_semana_53,
       count(*) FILTER (WHERE ref_years < 1) AS n_invalido
FROM risk_cell_week
GROUP BY era
ORDER BY era;

\echo ''
\echo '=== A3. Rangos (todo debe estar en 0 filas salvo el conteo informativo) ==='
SELECT count(*) FILTER (WHERE p_fire < 0 OR p_fire > 1)        AS p_fuera_rango,
       count(*) FILTER (WHERE p_lo   < 0 OR p_lo   > 1)        AS p_lo_fuera_rango,
       count(*) FILTER (WHERE p_hi   < 0 OR p_hi   > 1)        AS p_hi_fuera_rango,
       count(*) FILTER (WHERE p_lo > p_hi)                     AS intervalo_invertido,
       count(*) FILTER (WHERE fire_years > ref_years)          AS k_mayor_que_n,
       count(*) FILTER (WHERE fire_years < 0 OR fire_days < 0) AS negativos,
       count(*) FILTER (WHERE p_fire < p_lo OR p_fire > p_hi)  AS suavizado_fuera_ic
FROM risk_cell_week;

\echo ''
\echo '=== A4. Confiabilidad (debe sumar el total) ==='
SELECT reliability, count(*) AS filas
FROM risk_cell_week
GROUP BY 1
ORDER BY 1;

\echo ''
\echo '=== A5. Celdas-semana con actividad historica (k > 0) ==='
SELECT era,
       count(*) FILTER (WHERE fire_years > 0) AS con_fuego,
       count(*)                               AS total
FROM risk_cell_week
GROUP BY 1
ORDER BY 1;
