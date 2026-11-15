-- ============================================================================
-- Chronos Fire - Validacion §6.8
-- V3b: sensibilidad MODIS vs VIIRS con confianza high only
-- Base: chronos_fire (PostgreSQL 17)
-- Requiere: backend/db/validacion/00_contrato.sql
--
-- Uso:
--   & "C:\Program Files\PostgreSQL\17\bin\psql.exe" `
--     -h 127.0.0.1 -p 5432 -U postgres -d chronos_fire `
--     -f backend\db\validacion\03b_sensibilidad_high.sql
--
-- Diseno:
--   - Solo SP, 2013-2025, semanas 1-52.
--   - fire_cell = celda 0.05 grados con al menos una deteccion high de
--     vegetacion en la semana.
--   - Se compara con la serie nominal+high de 03_sensibilidad.sql.
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off
\pset border 2
\timing on
SET TIME ZONE 'UTC';

DROP TABLE IF EXISTS tmp_sensibilidad_high;

CREATE TEMP TABLE tmp_sensibilidad_high AS
WITH ambitos AS (
    SELECT 'nacional'::text AS ambito_id,
           'Nacional'::text AS name,
           NULL::double precision AS min_lat,
           NULL::double precision AS min_lon,
           NULL::double precision AS max_lat,
           NULL::double precision AS max_lon,
           'confirmed'::text AS status
    UNION ALL
    SELECT aoi_id, name, min_lat, min_lon, max_lat, max_lon, status
    FROM validation_aoi
),
base AS (
    SELECT a.ambito_id,
           d.sensor,
           extract(isoyear FROM d.acq_date)::smallint AS isoyear,
           extract(week FROM d.acq_date)::smallint    AS week_of_year,
           '0.05:' || CAST(round(d.latitude / 0.05) AS BIGINT) || ':' ||
                      CAST(round(d.longitude / 0.05) AS BIGINT) AS grid_id
    FROM ambitos a
    JOIN detections d
      ON d.source = 'SP'
     AND d.confidence_class = 'high'
     AND d.is_vegetation IS TRUE
     AND d.acq_date >= DATE '2013-01-01'
     AND d.acq_date <  DATE '2026-01-01'
     AND (
         a.min_lat IS NULL
         OR (
             d.latitude BETWEEN a.min_lat AND a.max_lat
             AND d.longitude BETWEEN a.min_lon AND a.max_lon
         )
     )
),
weekly AS (
    SELECT ambito_id, isoyear, week_of_year,
           count(DISTINCT grid_id) FILTER (WHERE sensor = 'MODIS')      AS modis,
           count(DISTINCT grid_id) FILTER (WHERE sensor = 'VIIRS_SNPP') AS snpp,
           count(DISTINCT grid_id)                                      AS todos
    FROM base
    GROUP BY 1, 2, 3
),
spine AS (
    SELECT a.ambito_id, a.name, a.status, y AS isoyear, w AS week_of_year
    FROM ambitos a
    CROSS JOIN generate_series(2013, 2025) AS y
    CROSS JOIN generate_series(1, 52) AS w
)
SELECT sp.ambito_id, sp.name, sp.status, sp.isoyear, sp.week_of_year,
       coalesce(w.modis, 0)::numeric AS modis,
       coalesce(w.snpp, 0)::numeric  AS snpp,
       coalesce(w.todos, 0)::numeric AS todos
FROM spine sp
LEFT JOIN weekly w
  ON w.ambito_id = sp.ambito_id
 AND w.isoyear = sp.isoyear
 AND w.week_of_year = sp.week_of_year;

ANALYZE tmp_sensibilidad_high;

\echo ''
\echo '=== V3b.1 High only: resumen por ambito (2013-2025, 52 semanas/anio) ==='
WITH r AS (
    SELECT *,
           rank() OVER (PARTITION BY ambito_id ORDER BY modis) AS rm,
           rank() OVER (PARTITION BY ambito_id ORDER BY snpp)  AS rs,
           rank() OVER (PARTITION BY ambito_id ORDER BY todos) AS rt
    FROM tmp_sensibilidad_high
)
SELECT ambito_id, name, status,
       count(*)                                      AS semanas,
       count(*) FILTER (WHERE modis = 0 AND snpp = 0) AS semanas_ambos_cero,
       round(corr(modis, snpp)::numeric, 3)           AS pearson_snpp,
       round(corr(rm, rs)::numeric, 3)                AS spearman_snpp,
       round(sum(snpp) / NULLIF(sum(modis), 0), 3)    AS ratio_snpp,
       round(corr(modis, todos)::numeric, 3)          AS pearson_todos,
       round(corr(rm, rt)::numeric, 3)                AS spearman_todos,
       round(sum(todos) / NULLIF(sum(modis), 0), 3)   AS ratio_todos
FROM r
GROUP BY 1, 2, 3
ORDER BY 1;

\echo ''
\echo '=== V3b.2 High only: solapamiento top-5 y top-10 por anio ==='
WITH top AS (
    SELECT *,
           row_number() OVER (
               PARTITION BY ambito_id, isoyear ORDER BY modis DESC, week_of_year
           ) AS tm,
           row_number() OVER (
               PARTITION BY ambito_id, isoyear ORDER BY snpp DESC, week_of_year
           ) AS ts
    FROM tmp_sensibilidad_high
)
SELECT ambito_id, name,
       count(DISTINCT isoyear) AS anios,
       round(count(*) FILTER (WHERE tm <= 5 AND ts <= 5)::numeric
             / (count(DISTINCT isoyear) * 5), 3) AS top5_overlap_snpp,
       round(count(*) FILTER (WHERE tm <= 10 AND ts <= 10)::numeric
             / (count(DISTINCT isoyear) * 10), 3) AS top10_overlap_snpp
FROM top
GROUP BY 1, 2
ORDER BY 1;

\echo ''
\echo '=== V3b.3 High only: periodos criticos en el anio del evento ==='
WITH eventos AS (
    SELECT DISTINCT w.aoi_id AS ambito_id, w.isoyear
    FROM validation_aoi_window w
),
f AS (
    SELECT s.*
    FROM tmp_sensibilidad_high s
    JOIN eventos e
      ON e.ambito_id = s.ambito_id
     AND e.isoyear = s.isoyear
),
r AS (
    SELECT *,
           row_number() OVER (
               PARTITION BY ambito_id, isoyear ORDER BY modis DESC, week_of_year
           ) AS tm,
           row_number() OVER (
               PARTITION BY ambito_id, isoyear ORDER BY snpp DESC, week_of_year
           ) AS ts
    FROM f
)
SELECT ambito_id, isoyear,
       round(count(*) FILTER (WHERE tm <= 5 AND ts <= 5)::numeric / 5, 3)
           AS top5_snpp,
       round(count(*) FILTER (WHERE tm <= 10 AND ts <= 10)::numeric / 10, 3)
           AS top10_snpp
FROM r
GROUP BY 1, 2
ORDER BY 1, 2;

\echo ''
\echo 'Fin de V3b.'
