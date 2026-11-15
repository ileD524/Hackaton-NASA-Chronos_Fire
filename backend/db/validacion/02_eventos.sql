-- ============================================================================
-- Chronos Fire - Validacion §6.8
-- V2: eventos conocidos
-- Base: chronos_fire (PostgreSQL 17)
-- Requiere: backend/db/validacion/00_contrato.sql
--
-- Uso:
--   & "C:\Program Files\PostgreSQL\17\bin\psql.exe" `
--     -h 127.0.0.1 -p 5432 -U postgres -d chronos_fire `
--     -f backend\db\validacion\02_eventos.sql
--
-- Reglas:
--   - observed = count(distinct grid_id) con fire_cells = 1 en la AOI/semana.
--   - baseline = mediana de observed en los anios de referencia, leave-one-out.
--   - observed y baseline usan el mismo sensor declarado en validation_aoi.
--   - n_ref_years >= 7 para mostrar; >= 10 y baseline >= 10 = high.
--   - observed >= 3 y baseline >= 3; si no, anomaly = NULL.
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off
\pset border 2
\timing on
SET TIME ZONE 'UTC';

\echo ''
\echo '=== Contrato de AOIs cargado ==='
SELECT aoi_id, name, min_lat, min_lon, max_lat, max_lon,
       observed_sensor, baseline_sensor, ref_from, ref_to, status
FROM validation_aoi
ORDER BY aoi_id;

\echo ''
\echo '=== V2.1 Eventos: observado, baseline, n_ref, anomalia y confianza ==='
WITH windows AS (
    SELECT a.aoi_id, a.name, a.observed_sensor, a.baseline_sensor,
           w.isoyear, w.expected_peak,
           generate_series(w.week_from::int, w.week_to::int)::smallint AS week_of_year
    FROM validation_aoi a
    JOIN validation_aoi_window w USING (aoi_id)
),
obs AS (
    SELECT a.aoi_id, g.isoyear, g.week_of_year,
           sum(g.fire_cells)::numeric AS observed
    FROM validation_aoi a
    JOIN windows w
      ON w.aoi_id = a.aoi_id
    JOIN grid_weekly_sensor g
      ON g.sensor = a.observed_sensor
     AND g.isoyear = w.isoyear
     AND g.week_of_year = w.week_of_year
     AND g.cell_lat BETWEEN a.min_lat AND a.max_lat
     AND g.cell_lon BETWEEN a.min_lon AND a.max_lon
    GROUP BY 1, 2, 3
),
ref AS (
    SELECT a.aoi_id, g.week_of_year, g.isoyear,
           sum(g.fire_cells)::numeric AS n
    FROM validation_aoi a
    JOIN grid_weekly_sensor g
      ON g.sensor = a.baseline_sensor
     AND g.isoyear BETWEEN a.ref_from AND a.ref_to
     AND g.cell_lat BETWEEN a.min_lat AND a.max_lat
     AND g.cell_lon BETWEEN a.min_lon AND a.max_lon
    GROUP BY 1, 2, 3
),
eval AS (
    SELECT w.aoi_id, w.name, w.isoyear, w.week_of_year, w.expected_peak,
           o.observed,
           (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY r.n)
            FROM ref r
            WHERE r.aoi_id = w.aoi_id
              AND r.week_of_year = w.week_of_year
              AND r.isoyear <> w.isoyear) AS baseline,
           (SELECT count(*)
            FROM ref r
            WHERE r.aoi_id = w.aoi_id
              AND r.week_of_year = w.week_of_year
              AND r.isoyear <> w.isoyear) AS n_ref_years
    FROM windows w
    LEFT JOIN obs o
      ON o.aoi_id = w.aoi_id
     AND o.isoyear = w.isoyear
     AND o.week_of_year = w.week_of_year
)
SELECT aoi_id,
       name,
       isoyear,
       week_of_year,
       expected_peak,
       observed,
       baseline,
       n_ref_years,
       CASE
           WHEN observed >= 3 AND baseline >= 3 AND n_ref_years >= 7
           THEN round((observed / baseline)::numeric, 2)
       END AS anomaly,
       CASE
           WHEN observed IS NULL OR baseline IS NULL OR n_ref_years < 7
             OR observed < 3 OR baseline < 3 THEN 'insufficient'
           WHEN n_ref_years >= 10 AND baseline >= 10 THEN 'high'
           ELSE 'low'
       END AS confidence
FROM eval
ORDER BY aoi_id, isoyear, week_of_year;

\echo ''
\echo '=== V2.2 Resumen por evento: pico observado vs semana de referencia ==='
WITH windows AS (
    SELECT a.aoi_id, a.name, a.observed_sensor, a.baseline_sensor,
           w.isoyear, w.expected_peak, w.week_from, w.week_to,
           generate_series(w.week_from::int, w.week_to::int)::smallint AS week_of_year
    FROM validation_aoi a
    JOIN validation_aoi_window w USING (aoi_id)
),
obs AS (
    SELECT a.aoi_id, g.isoyear, g.week_of_year,
           sum(g.fire_cells)::numeric AS observed
    FROM validation_aoi a
    JOIN windows w ON w.aoi_id = a.aoi_id
    JOIN grid_weekly_sensor g
      ON g.sensor = a.observed_sensor
     AND g.isoyear = w.isoyear
     AND g.week_of_year = w.week_of_year
     AND g.cell_lat BETWEEN a.min_lat AND a.max_lat
     AND g.cell_lon BETWEEN a.min_lon AND a.max_lon
    GROUP BY 1, 2, 3
),
ref AS (
    SELECT a.aoi_id, g.week_of_year, g.isoyear,
           sum(g.fire_cells)::numeric AS n
    FROM validation_aoi a
    JOIN grid_weekly_sensor g
      ON g.sensor = a.baseline_sensor
     AND g.isoyear BETWEEN a.ref_from AND a.ref_to
     AND g.cell_lat BETWEEN a.min_lat AND a.max_lat
     AND g.cell_lon BETWEEN a.min_lon AND a.max_lon
    GROUP BY 1, 2, 3
),
semana AS (
    SELECT w.aoi_id, w.name, w.isoyear, w.week_from, w.week_to, w.expected_peak,
           w.week_of_year, o.observed,
           (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY r.n)
            FROM ref r
            WHERE r.aoi_id = w.aoi_id
              AND r.week_of_year = w.week_of_year
              AND r.isoyear <> w.isoyear) AS baseline,
           (SELECT count(*)
            FROM ref r
            WHERE r.aoi_id = w.aoi_id
              AND r.week_of_year = w.week_of_year
              AND r.isoyear <> w.isoyear) AS n_ref_years
    FROM windows w
    LEFT JOIN obs o
      ON o.aoi_id = w.aoi_id
     AND o.isoyear = w.isoyear
     AND o.week_of_year = w.week_of_year
),
calc AS (
    SELECT *,
           CASE
               WHEN observed >= 3 AND baseline >= 3 AND n_ref_years >= 7
               THEN round((observed / baseline)::numeric, 2)
           END AS anomaly
    FROM semana
),
peak AS (
    SELECT *,
           row_number() OVER (
               PARTITION BY aoi_id, isoyear, week_from, week_to
               ORDER BY observed DESC NULLS LAST, week_of_year
           ) AS rn
    FROM calc
),
refrow AS (
    SELECT * FROM calc WHERE week_of_year = expected_peak
)
SELECT p.aoi_id, p.name, p.isoyear, p.week_from, p.week_to,
       p.expected_peak          AS semana_referencia,
       p.week_of_year           AS peak_observado,
       p.observed               AS observed_en_pico,
       p.anomaly                AS anomaly_en_pico,
       r.observed               AS observed_en_referencia,
       r.anomaly                AS anomaly_en_referencia,
       (p.observed >= 3 AND p.anomaly > 1 AND p.n_ref_years >= 7) AS cumple_evento
FROM peak p
LEFT JOIN refrow r
  ON r.aoi_id = p.aoi_id
 AND r.isoyear = p.isoyear
 AND r.week_from = p.week_from
 AND r.week_to = p.week_to
WHERE p.rn = 1
ORDER BY p.aoi_id, p.isoyear;

\echo ''
\echo 'Fin de V2.'
