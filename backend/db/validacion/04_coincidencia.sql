-- ============================================================================
-- Chronos Fire - Validacion §6.8
-- V4: coincidencia espacial MODIS vs VIIRS
-- Base: chronos_fire (PostgreSQL 17)
--
-- Uso (un anio):
--   & "C:\Program Files\PostgreSQL\17\bin\psql.exe" `
--     -h 127.0.0.1 -p 5432 -U postgres -d chronos_fire `
--     -v anio=2020 -v viirs_sensor=VIIRS_SNPP -v max_hours=24 `
--     -f backend\db\validacion\04_coincidencia.sql
--
-- Variables:
--   anio          anio a evaluar (default 2020)
--   viirs_sensor  VIIRS_SNPP | VIIRS_NOAA20 | VIIRS_NOAA21 (default VIIRS_SNPP)
--   max_hours     ventana horaria maxima (default 24 = mismo dia)
--
-- Metodo:
--   - Solo SP.
--   - Join por bin de 0.02 grados y mismo acq_date.
--   - Distancia equirectangular < 1 km.
--   - Se cuentan detecciones distintas, no pares.
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off
\pset border 2
\timing on
SET TIME ZONE 'UTC';

\if :{?anio}
\else
\set anio 2020
\endif

\if :{?viirs_sensor}
\else
\set viirs_sensor 'VIIRS_SNPP'
\endif

\if :{?max_hours}
\else
\set max_hours 24
\endif

\echo ''
\echo '=== V4 Coincidencia espacial ==='
\echo 'Anio:' :anio ' | VIIRS:' :'viirs_sensor' ' | max_hours:' :max_hours

WITH m AS (
    SELECT latitude, longitude, acq_date, acquired_at,
           frp_mw, frp_density_wm2,
           round(latitude / 0.02)::int  AS la,
           round(longitude / 0.02)::int AS lo
    FROM detections
    WHERE sensor = 'MODIS'
      AND source = 'SP'
      AND acq_date >= make_date(:anio, 1, 1)
      AND acq_date <  make_date(:anio + 1, 1, 1)
),
v AS (
    SELECT latitude, longitude, acq_date, acquired_at,
           frp_mw, frp_density_wm2,
           round(latitude / 0.02)::int  AS la,
           round(longitude / 0.02)::int AS lo
    FROM detections
    WHERE sensor = :'viirs_sensor'
      AND source = 'SP'
      AND acq_date >= make_date(:anio, 1, 1)
      AND acq_date <  make_date(:anio + 1, 1, 1)
),
pares AS (
    SELECT m.latitude  AS mlat,
           m.longitude AS mlon,
           m.acq_date  AS md,
           m.acquired_at AS mt,
           m.frp_mw    AS mfrp,
           m.frp_density_wm2 AS mdens,
           v.latitude  AS vlat,
           v.longitude AS vlon,
           v.acquired_at AS vt,
           v.frp_mw    AS vfrp,
           v.frp_density_wm2 AS vdens,
           sqrt(
               power((m.latitude - v.latitude) * 111.32, 2)
               + power((m.longitude - v.longitude) * 111.32
                       * cos(radians(m.latitude)), 2)
           ) AS km,
           abs(extract(epoch FROM (m.acquired_at - v.acquired_at))) / 3600.0 AS horas
    FROM m
    CROSS JOIN (
        VALUES (-1, -1), (-1, 0), (-1, 1),
               ( 0, -1), ( 0, 0), ( 0, 1),
               ( 1, -1), ( 1, 0), ( 1, 1)
    ) AS off(dy, dx)
    JOIN v
      ON v.acq_date = m.acq_date
     AND v.la = m.la + off.dy
     AND v.lo = m.lo + off.dx
),
ok AS (
    SELECT * FROM pares
    WHERE km < 1.0
      AND horas <= :max_hours
),
m_match AS (
    SELECT mlat, mlon, mt,
           bool_or(km < 1.0 AND horas <= :max_hours) AS matched
    FROM pares
    GROUP BY 1, 2, 3
),
v_match AS (
    SELECT vlat, vlon, vt,
           bool_or(km < 1.0 AND horas <= :max_hours) AS matched
    FROM pares
    GROUP BY 1, 2, 3
),
m_all AS (
    SELECT m.*, coalesce(mm.matched, false) AS matched
    FROM m
    LEFT JOIN m_match mm
      ON mm.mlat = m.latitude
     AND mm.mlon = m.longitude
     AND mm.mt = m.acquired_at
),
v_all AS (
    SELECT v.*, coalesce(vm.matched, false) AS matched
    FROM v
    LEFT JOIN v_match vm
      ON vm.vlat = v.latitude
     AND vm.vlon = v.longitude
     AND vm.vt = v.acquired_at
)
SELECT
    (SELECT count(*) FROM m) AS modis_total,
    (SELECT count(*) FROM v) AS viirs_total,
    (SELECT count(DISTINCT (mlat, mlon, mt)) FROM ok) AS modis_coincidentes,
    (SELECT count(DISTINCT (vlat, vlon, vt)) FROM ok) AS viirs_coincidentes,
    (SELECT count(*) FROM ok) AS pares_1km,
    round((SELECT count(DISTINCT (mlat, mlon, mt)) FROM ok)::numeric
          / NULLIF((SELECT count(*) FROM m), 0), 3) AS pct_modis,
    round((SELECT count(DISTINCT (vlat, vlon, vt)) FROM ok)::numeric
          / NULLIF((SELECT count(*) FROM v), 0), 3) AS pct_viirs,
    round((SELECT count(*) FROM ok)::numeric
          / NULLIF((SELECT count(DISTINCT (mlat, mlon, mt)) FROM ok), 0), 2)
          AS pares_por_modis_coincidente,
    (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY mfrp) FROM ok)   AS med_frp_modis_match,
    (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY vfrp) FROM ok)   AS med_frp_viirs_match,
    (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY mdens) FROM ok)  AS med_dens_modis_match,
    (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY vdens) FROM ok)  AS med_dens_viirs_match,
    (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY frp_mw)
     FROM m_all WHERE matched)                                           AS med_frp_modis_matched,
    (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY frp_mw)
     FROM m_all WHERE NOT matched)                                       AS med_frp_modis_unmatched,
    (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY frp_mw)
     FROM v_all WHERE matched)                                           AS med_frp_viirs_matched,
    (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY frp_mw)
     FROM v_all WHERE NOT matched)                                       AS med_frp_viirs_unmatched,
    (SELECT count(*) FROM pares WHERE km < 0.5)                          AS pares_lt_500m,
    (SELECT count(*) FROM pares WHERE km >= 0.5 AND km < 1.0)            AS pares_500m_1km,
    (SELECT count(*) FROM pares WHERE km >= 1.0 AND km < 1.5)            AS pares_1km_1_5km,
    (SELECT count(*) FROM pares WHERE km >= 1.5)                         AS pares_ge_1_5km,
    (SELECT round(percentile_cont(0.5) WITHIN GROUP (ORDER BY km)::numeric, 2)
     FROM pares WHERE km < 1.0)                                          AS mediana_km_lt_1km;

\echo ''
\echo 'Fin de V4.'
