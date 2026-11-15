-- ============================================================================
-- Chronos Fire - Validacion §6.8
-- V3: sensibilidad MODIS vs VIIRS
-- Base: chronos_fire (PostgreSQL 17)
-- Requiere: backend/db/validacion/00_contrato.sql
--
-- Uso:
--   & "C:\Program Files\PostgreSQL\17\bin\psql.exe" `
--     -h 127.0.0.1 -p 5432 -U postgres -d chronos_fire `
--     -f backend\db\validacion\03_sensibilidad.sql
--
-- Diseno:
--   - Periodo comun: 2013-2025, semanas ISO 1-52.
--   - Series: MODIS-only, VIIRS_SNPP-only, todos los VIIRS.
--   - Ambitos: nacional + cada AOI de validation_aoi.
--   - Espina completa de 52 semanas por anio; las ausencias se rellenan con 0
--     y se reporta cuantas semanas tienen ambos sensores en cero.
--   - Metrica principal: solapamiento top-5 de semanas criticas.
--   - Metrica secundaria: Spearman/Pearson y ratio agregado.
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off
\pset border 2
\timing on
SET TIME ZONE 'UTC';

DROP TABLE IF EXISTS tmp_sensibilidad;

CREATE TEMP TABLE tmp_sensibilidad AS
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
spine AS (
    SELECT a.ambito_id, a.name, a.status, y AS isoyear, w AS week_of_year
    FROM ambitos a
    CROSS JOIN generate_series(2013, 2025) AS y
    CROSS JOIN generate_series(1, 52) AS w
),
s AS (
    SELECT a.ambito_id, g.isoyear, g.week_of_year,
           sum(g.fire_cells) FILTER (WHERE g.sensor = 'MODIS')       AS modis,
           sum(g.fire_cells) FILTER (WHERE g.sensor = 'VIIRS_SNPP')  AS snpp,
           count(DISTINCT g.grid_id) FILTER (
               WHERE g.sensor LIKE 'VIIRS%' AND g.fire_cells = 1
           ) AS viirs_all
    FROM ambitos a
    JOIN grid_weekly_sensor g
      ON g.isoyear BETWEEN 2013 AND 2025
     AND (
         a.min_lat IS NULL
         OR (
             g.cell_lat BETWEEN a.min_lat AND a.max_lat
             AND g.cell_lon BETWEEN a.min_lon AND a.max_lon
         )
     )
    GROUP BY 1, 2, 3
),
t AS (
    SELECT a.ambito_id, g.isoyear, g.week_of_year,
           sum(g.fire_cells) AS todos
    FROM ambitos a
    JOIN grid_weekly g
      ON g.isoyear BETWEEN 2013 AND 2025
     AND (
         a.min_lat IS NULL
         OR (
             g.cell_lat BETWEEN a.min_lat AND a.max_lat
             AND g.cell_lon BETWEEN a.min_lon AND a.max_lon
         )
     )
    GROUP BY 1, 2, 3
)
SELECT sp.ambito_id, sp.name, sp.status, sp.isoyear, sp.week_of_year,
       coalesce(s.modis, 0)::numeric     AS modis,
       coalesce(s.snpp, 0)::numeric      AS snpp,
       coalesce(s.viirs_all, 0)::numeric AS viirs_all,
       coalesce(t.todos, 0)::numeric     AS todos
FROM spine sp
LEFT JOIN s
  ON s.ambito_id = sp.ambito_id
 AND s.isoyear = sp.isoyear
 AND s.week_of_year = sp.week_of_year
LEFT JOIN t
  ON t.ambito_id = sp.ambito_id
 AND t.isoyear = sp.isoyear
 AND t.week_of_year = sp.week_of_year;

ANALYZE tmp_sensibilidad;

\echo ''
\echo '=== V3.1 Resumen por ambito (2013-2025, 52 semanas/anio, cero-relleno) ==='
WITH r AS (
    SELECT *,
           rank() OVER (PARTITION BY ambito_id ORDER BY modis)     AS rm,
           rank() OVER (PARTITION BY ambito_id ORDER BY snpp)      AS rs,
           rank() OVER (PARTITION BY ambito_id ORDER BY viirs_all) AS rv
    FROM tmp_sensibilidad
)
SELECT ambito_id, name, status,
       count(*)                                      AS semanas,
       count(*) FILTER (WHERE modis = 0 AND snpp = 0) AS semanas_ambos_cero,
       round(corr(modis, snpp)::numeric, 3)           AS pearson_snpp,
       round(corr(rm, rs)::numeric, 3)                AS spearman_snpp,
       round(sum(snpp) / NULLIF(sum(modis), 0), 3)    AS ratio_snpp,
       round(corr(modis, viirs_all)::numeric, 3)      AS pearson_viirs,
       round(corr(rm, rv)::numeric, 3)                AS spearman_viirs,
       round(sum(viirs_all) / NULLIF(sum(modis), 0), 3) AS ratio_viirs
FROM r
GROUP BY 1, 2, 3
ORDER BY 1;

\echo ''
\echo '=== V3.2 Solapamiento de semanas criticas (top-5 y top-10 por anio) ==='
WITH top AS (
    SELECT *,
           row_number() OVER (
               PARTITION BY ambito_id, isoyear ORDER BY modis DESC, week_of_year
           ) AS tm,
           row_number() OVER (
               PARTITION BY ambito_id, isoyear ORDER BY snpp DESC, week_of_year
           ) AS ts,
           row_number() OVER (
               PARTITION BY ambito_id, isoyear ORDER BY viirs_all DESC, week_of_year
           ) AS tv
    FROM tmp_sensibilidad
)
SELECT ambito_id, name,
       count(DISTINCT isoyear) AS anios,
       count(*) FILTER (WHERE tm <= 5 AND ts <= 5)  AS top5_comunes_snpp,
       round(count(*) FILTER (WHERE tm <= 5 AND ts <= 5)::numeric
             / (count(DISTINCT isoyear) * 5), 3) AS top5_overlap_snpp,
       count(*) FILTER (WHERE tm <= 10 AND ts <= 10) AS top10_comunes_snpp,
       round(count(*) FILTER (WHERE tm <= 10 AND ts <= 10)::numeric
             / (count(DISTINCT isoyear) * 10), 3) AS top10_overlap_snpp,
       round(count(*) FILTER (WHERE tm <= 5 AND tv <= 5)::numeric
             / (count(DISTINCT isoyear) * 5), 3) AS top5_overlap_viirs,
       round(count(*) FILTER (WHERE tm <= 10 AND tv <= 10)::numeric
             / (count(DISTINCT isoyear) * 10), 3) AS top10_overlap_viirs
FROM top
GROUP BY 1, 2
ORDER BY 1;

\echo ''
\echo '=== V3.3 Calendario combinado (todos) vs cada sensor ==='
WITH r AS (
    SELECT *,
           rank() OVER (PARTITION BY ambito_id ORDER BY modis) AS rm,
           rank() OVER (PARTITION BY ambito_id ORDER BY snpp)  AS rs,
           rank() OVER (PARTITION BY ambito_id ORDER BY todos) AS rt
    FROM tmp_sensibilidad
)
SELECT ambito_id, name,
       round(corr(modis, todos)::numeric, 3)          AS pearson_modis_todos,
       round(corr(rm, rt)::numeric, 3)                AS spearman_modis_todos,
       round(sum(todos) / NULLIF(sum(modis), 0), 3)   AS ratio_todos_modis,
       round(corr(snpp, todos)::numeric, 3)           AS pearson_snpp_todos,
       round(corr(rs, rt)::numeric, 3)                AS spearman_snpp_todos,
       round(sum(todos) / NULLIF(sum(snpp), 0), 3)    AS ratio_todos_snpp
FROM r
GROUP BY 1, 2
ORDER BY 1;

\echo ''
\echo '=== V3.4 Periodos criticos en el anio del evento ==='
WITH eventos AS (
    SELECT DISTINCT w.aoi_id AS ambito_id, w.isoyear
    FROM validation_aoi_window w
),
f AS (
    SELECT s.*
    FROM tmp_sensibilidad s
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
           ) AS ts,
           row_number() OVER (
               PARTITION BY ambito_id, isoyear ORDER BY todos DESC, week_of_year
           ) AS tt,
           rank() OVER (PARTITION BY ambito_id, isoyear ORDER BY modis) AS rm,
           rank() OVER (PARTITION BY ambito_id, isoyear ORDER BY snpp)  AS rs,
           rank() OVER (PARTITION BY ambito_id, isoyear ORDER BY todos) AS rt
    FROM f
)
SELECT ambito_id, isoyear,
       round(corr(rm, rs)::numeric, 3)                                     AS spearman_snpp,
       round(count(*) FILTER (WHERE tm <= 5 AND ts <= 5)::numeric / 5, 3)  AS top5_snpp,
       round(count(*) FILTER (WHERE tm <= 10 AND ts <= 10)::numeric / 10, 3) AS top10_snpp,
       round(corr(rm, rt)::numeric, 3)                                     AS spearman_todos,
       round(count(*) FILTER (WHERE tm <= 5 AND tt <= 5)::numeric / 5, 3)  AS top5_todos,
       round(count(*) FILTER (WHERE tm <= 10 AND tt <= 10)::numeric / 10, 3) AS top10_todos
FROM r
GROUP BY 1, 2
ORDER BY 1, 2;

\echo ''
\echo '=== V3.5 Detalle por anio y ambito (MODIS vs SNPP) ==='
WITH r AS (
    SELECT *,
           rank() OVER (PARTITION BY ambito_id, isoyear ORDER BY modis) AS rm,
           rank() OVER (PARTITION BY ambito_id, isoyear ORDER BY snpp)  AS rs
    FROM tmp_sensibilidad
)
SELECT ambito_id, name, isoyear,
       round(corr(modis, snpp)::numeric, 3)          AS pearson,
       round(corr(rm, rs)::numeric, 3)               AS spearman,
       round(sum(snpp) / NULLIF(sum(modis), 0), 3)   AS ratio
FROM r
GROUP BY 1, 2, 3
ORDER BY 1, 3;

\echo ''
\echo 'Fin de V3.'
