-- ============================================================================
-- Chronos Fire - Validacion de la capa de riesgo estadistico
-- Fase 1 de PLAN_RIESGO_ESTADISTICO.md
-- Base: chronos_fire (PostgreSQL 17)
--
-- Uso:
--   & "C:\Program Files\PostgreSQL\17\bin\psql.exe" `
--     -h 127.0.0.1 -p 5432 -U postgres -d chronos_fire `
--     -f backend\db\validacion\06_riesgo.sql
--
-- Solo lectura. No crea ni modifica datos.
-- Cada chequeo de falla devuelve 0 filas cuando esta OK.
-- La seccion "informativo" puede devolver filas: documenta el desvio esperado
-- entre p_fire (media posterior) y el intervalo de Wilson sobre k/n.
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off
\pset border 2
\timing on
SET TIME ZONE 'UTC';

\echo ''
\echo '=== R0. Objetos y volumen ==='
SELECT to_regclass('public.risk_prior_week') IS NOT NULL AS prior_existe,
       to_regclass('public.risk_cell_week')  IS NOT NULL AS cell_week_existe,
       to_regclass('public.risk_backtest')   IS NOT NULL AS backtest_existe,
       (SELECT count(*) FROM risk_prior_week) AS filas_prior,
       (SELECT count(*) FROM risk_cell_week)  AS filas_cell_week,
       (SELECT count(*) FROM coverage_daily WHERE has_data) AS filas_cobertura;

\echo ''
\echo '=== R1. cobertura_daily: filas sin cobertura o fuera de rango (0 filas = OK) ==='
SELECT region, sensor, source, acq_date
FROM coverage_daily
WHERE NOT has_data
   OR coverage_type NOT IN ('proxy', 'observed')
   OR acq_date < DATE '2000-01-01'
ORDER BY acq_date DESC
LIMIT 20;

\echo ''
\echo '=== R2. Semanas ISO de risk_cell_week vs cobertura cargada (0 filas = OK) ==='
-- Contra la cobertura, no contra el calendario teorico: valida que el rebuild
-- haya usado el mismo n_ref que coverage_daily. Los huecos de la propia
-- cobertura se auditan en R2b.
WITH cov AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           extract(week FROM acq_date)::smallint AS week_of_year,
           count(DISTINCT extract(isoyear FROM acq_date))::int AS n_ref
    FROM coverage_daily
    WHERE has_data
      AND (
            (sensor = 'MODIS'      AND extract(isoyear FROM acq_date) BETWEEN 2003 AND 2011)
         OR (sensor = 'VIIRS_SNPP' AND extract(isoyear FROM acq_date) BETWEEN 2013 AND 2025)
      )
    GROUP BY 1, 2
),
real AS (
    SELECT era, week_of_year, min(ref_years) AS n_ref
    FROM risk_cell_week GROUP BY 1, 2
)
SELECT e.era, e.week_of_year, e.n_ref AS n_esperado, r.n_ref AS n_real
FROM cov e
LEFT JOIN real r USING (era, week_of_year)
WHERE r.n_ref IS NULL OR r.n_ref <> e.n_ref
ORDER BY 1, 2;

\echo ''
\echo '=== R2b. Huecos de cobertura frente al calendario ISO (informativo) ==='
-- Semanas que existen en el calendario pero tienen menos anios con datos.
-- Caso conocido: SNPP 2022 semana 31 (no hay dias cargados ese tramo).
WITH cal AS (
    SELECT DISTINCT 'MODIS'::text AS era,
           extract(isoyear FROM d)::int      AS isoyear,
           extract(week    FROM d)::smallint AS week_of_year
    FROM generate_series('2003-01-01'::date, '2011-12-31'::date, '1 day') AS d
    WHERE extract(isoyear FROM d) BETWEEN 2003 AND 2011
    UNION ALL
    SELECT DISTINCT 'SNPP'::text,
           extract(isoyear FROM d)::int,
           extract(week    FROM d)::smallint
    FROM generate_series('2013-01-01'::date, '2025-12-31'::date, '1 day') AS d
    WHERE extract(isoyear FROM d) BETWEEN 2013 AND 2025
),
esperado AS (SELECT era, week_of_year, count(*)::int AS n_calendario FROM cal GROUP BY 1, 2),
real AS (
    SELECT era, week_of_year, min(ref_years) AS n_ref
    FROM risk_cell_week GROUP BY 1, 2
)
SELECT e.era, e.week_of_year, e.n_calendario, r.n_ref,
       e.n_calendario - r.n_ref AS anios_faltantes
FROM esperado e
JOIN real r USING (era, week_of_year)
WHERE r.n_ref < e.n_calendario
ORDER BY 1, 2;

\echo ''
\echo '=== R3. risk_prior_week: filas, mu y parametros invalidos (0 filas = OK) ==='
SELECT era, week_of_year, mu, kappa, alpha, beta
FROM risk_prior_week
WHERE mu < 0 OR mu > 1
   OR kappa <= 0
   OR alpha <= 0
   OR beta < 0
   OR abs((alpha + beta) - kappa) > 0.001
   OR era NOT IN ('MODIS', 'SNPP')
   OR week_of_year NOT BETWEEN 1 AND 53;

\echo ''
\echo '=== R4. risk_prior_week: 53 semanas por era (0 filas = OK) ==='
SELECT era, 53 - count(*) AS semanas_faltantes
FROM risk_prior_week
GROUP BY era
HAVING count(*) <> 53;

\echo ''
\echo '=== R5. Celdas-semana sin prior (0 filas = OK) ==='
SELECT r.era, count(*) AS sin_prior
FROM risk_cell_week r
LEFT JOIN risk_prior_week p
       ON p.era = r.era AND p.week_of_year = r.week_of_year
WHERE p.era IS NULL
GROUP BY r.era;

\echo ''
\echo '=== R6. Filas con valores fuera de rango o relaciones invalidas (0 filas = OK) ==='
SELECT grid_id, week_of_year, era, ref_years, fire_years, fire_days, p_fire, p_lo, p_hi
FROM risk_cell_week
WHERE ref_years < 1
   OR fire_years < 0
   OR fire_days < 0
   OR fire_years > ref_years
   OR p_fire < 0 OR p_fire > 1
   OR p_lo   < 0 OR p_lo   > 1
   OR p_hi   < 0 OR p_hi   > 1
   OR p_lo > p_hi
   OR era NOT IN ('MODIS', 'SNPP')
LIMIT 20;

\echo ''
\echo '=== R7. Filas con reliability incoherente con ref_years (0 filas = OK) ==='
SELECT grid_id, week_of_year, era, ref_years, reliability
FROM risk_cell_week
WHERE reliability IS DISTINCT FROM
      (CASE WHEN ref_years >= 10 THEN 'high'
            WHEN ref_years >= 5  THEN 'low'
            ELSE 'insufficient' END)
LIMIT 20;

\echo ''
\echo '=== R8. Claves duplicadas o nulas (0 filas = OK) ==='
SELECT grid_id, week_of_year, era, count(*) AS repeticiones
FROM risk_cell_week
GROUP BY 1, 2, 3
HAVING count(*) > 1
LIMIT 20;

\echo ''
\echo '=== R9. Celdas sin ninguna fila de riesgo (0 filas = OK) ==='
WITH celdas AS (SELECT DISTINCT grid_id FROM grid_weekly),
riesgo AS (SELECT DISTINCT grid_id FROM risk_cell_week)
SELECT c.grid_id
FROM celdas c
LEFT JOIN riesgo r USING (grid_id)
WHERE r.grid_id IS NULL
LIMIT 20;

\echo ''
\echo '=== R10. Cobertura del producto por era (informativo) ==='
SELECT era,
       count(*)                                        AS celdas_semana,
       count(*) FILTER (WHERE reliability = 'high')     AS high,
       count(*) FILTER (WHERE reliability = 'low')      AS low,
       count(*) FILTER (WHERE reliability = 'insufficient') AS insufficient,
       count(*) FILTER (WHERE fire_years > 0)           AS con_fuego_historico
FROM risk_cell_week
GROUP BY era
ORDER BY era;

\echo ''
\echo '=== R11. Desvio esperado: p_fire (suavizado) fuera del Wilson sobre k/n (informativo) ==='
-- Con k = 0 el prior empuja p_fire por encima de p_lo = 0; con k = n el
-- suavizado lo empuja por debajo de p_lo. No es un error, se documenta.
SELECT era,
       count(*)                                                       AS filas,
       count(*) FILTER (WHERE p_fire < p_lo OR p_fire > p_hi)         AS fuera_intervalo,
       round(100.0 * count(*) FILTER (WHERE p_fire < p_lo OR p_fire > p_hi)
             / count(*), 2)                                           AS pct_fuera,
       round(max(greatest(p_lo - p_fire, p_fire - p_hi))::numeric, 4) AS desvio_maximo
FROM risk_cell_week
GROUP BY era
ORDER BY era;

\echo ''
\echo '=== R12. Invariantes de regresion (no se tocaron las tablas canonicas) ==='
SELECT 'detections'       AS objeto, 7082474 AS esperado, count(*) AS real FROM detections
UNION ALL
SELECT 'grid_daily',      2103053, count(*) FROM grid_daily
UNION ALL
SELECT 'calendar_weekly', 3719116, count(*) FROM calendar_weekly;

\echo ''
\echo '=== R13. Indices requeridos (0 filas = OK) ==='
SELECT falta
FROM (VALUES
        ('risk_cell_week_week_idx'),
        ('risk_cell_week_latlon_idx'),
        ('grid_weekly_sensor_cell_week_idx')
     ) AS v(falta)
WHERE to_regclass('public.' || falta) IS NULL;

\echo ''
\echo '=== R14. Resumen de fallas (todo en 0 = OK) ==='
WITH cov AS (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           extract(week FROM acq_date)::smallint AS week_of_year,
           count(DISTINCT extract(isoyear FROM acq_date))::int AS n_ref
    FROM coverage_daily
    WHERE has_data
      AND (
            (sensor = 'MODIS'      AND extract(isoyear FROM acq_date) BETWEEN 2003 AND 2011)
         OR (sensor = 'VIIRS_SNPP' AND extract(isoyear FROM acq_date) BETWEEN 2013 AND 2025)
      )
    GROUP BY 1, 2
),
real_ref AS (SELECT era, week_of_year, min(ref_years) AS n_ref FROM risk_cell_week GROUP BY 1, 2)
SELECT
    (SELECT count(*) FROM coverage_daily WHERE NOT has_data) AS cobertura_invalida,
    (SELECT count(*) FROM cov e LEFT JOIN real_ref r USING (era, week_of_year)
      WHERE r.n_ref IS NULL OR r.n_ref <> e.n_ref)           AS semanas_desalineadas,
    (SELECT count(*) FROM risk_prior_week
      WHERE mu < 0 OR mu > 1 OR alpha <= 0 OR beta < 0)      AS prior_invalido,
    (SELECT count(*) FROM risk_cell_week
      WHERE ref_years < 1 OR fire_years < 0 OR fire_years > ref_years
         OR p_fire < 0 OR p_fire > 1 OR p_lo > p_hi)         AS celdas_invalidas,
    (SELECT count(*) FROM risk_cell_week r
      WHERE r.reliability IS DISTINCT FROM
            (CASE WHEN ref_years >= 10 THEN 'high'
                  WHEN ref_years >= 5  THEN 'low'
                  ELSE 'insufficient' END))                  AS reliability_invalida;
