-- ============================================================================
-- Chronos Fire - Validacion §6.8
-- V1: validacion interna
-- Base: chronos_fire (PostgreSQL 17)
--
-- Uso:
--   & "C:\Program Files\PostgreSQL\17\bin\psql.exe" `
--     -h 127.0.0.1 -p 5432 -U postgres -d chronos_fire `
--     -f backend\db\validacion\01_interna.sql
--
-- Solo lectura. No crea ni modifica datos.
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off
\pset border 2
\timing on
SET TIME ZONE 'UTC';

\echo ''
\echo '=== V1.1 Fuentes, volumen y rango temporal ==='
SELECT sensor, source,
       count(*)                    AS n,
       min(acq_date)               AS desde,
       max(acq_date)               AS hasta,
       count(DISTINCT acq_date)    AS dias_con_deteccion
FROM detections
GROUP BY 1, 2
ORDER BY 1, 2;

\echo ''
\echo '=== V1.2 Cobertura observada por anio y sensor (cota inferior) ==='
WITH base AS (
    SELECT sensor, source, acq_date,
           extract(year FROM acq_date)::int AS anio
    FROM detections
),
dias AS (
    SELECT anio,
           (make_date(anio, 1, 1) + interval '1 year - 1 day')::date
           - make_date(anio, 1, 1) + 1 AS dias_anio,
           least(
               (make_date(anio, 1, 1) + interval '1 year - 1 day')::date
               - make_date(anio, 1, 1) + 1,
               current_date - make_date(anio, 1, 1) + 1
           ) AS dias_efectivos
    FROM generate_series(2000, 2026) AS anio
)
SELECT b.sensor,
       b.anio,
       d.dias_anio,
       d.dias_efectivos,
       count(DISTINCT b.acq_date)                                   AS dias_con_deteccion,
       round(100.0 * count(DISTINCT b.acq_date) / d.dias_efectivos, 1) AS pct_dias,
       count(DISTINCT b.acq_date) FILTER (WHERE b.source = 'SP')     AS dias_sp,
       count(DISTINCT b.acq_date) FILTER (WHERE b.source = 'NRT')    AS dias_nrt
FROM base b
JOIN dias d USING (anio)
GROUP BY b.sensor, b.anio, d.dias_anio, d.dias_efectivos
ORDER BY b.sensor, b.anio;

\echo ''
\echo '=== V1.3 Duplicados exactos: PK de detections ==='
SELECT conname, pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE conrelid = 'detections'::regclass
  AND contype = 'p';

\echo 'Chequeo cross-source: si V1.4 = 0, no puede haber duplicados entre SP y NRT.'
\echo 'Opcional (pesado) para cuando V1.4 > 0:'
\echo '  SELECT count(*) FROM ('
\echo '    SELECT sensor, latitude, longitude, acquired_at'
\echo '    FROM detections GROUP BY 1,2,3,4'
\echo '    HAVING count(DISTINCT source) > 1'
\echo '  ) x;'

\echo ''
\echo '=== V1.4 Solapamiento SP/NRT por sensor y fecha ==='
WITH pares AS (
    SELECT sensor, acq_date,
           count(*) FILTER (WHERE source = 'SP')  AS sp,
           count(*) FILTER (WHERE source = 'NRT') AS nrt
    FROM detections
    GROUP BY 1, 2
    HAVING count(*) FILTER (WHERE source = 'SP')  > 0
       AND count(*) FILTER (WHERE source = 'NRT') > 0
)
SELECT count(*)                        AS pares_sensor_fecha_con_ambas,
       coalesce(sum(sp), 0)::bigint    AS det_sp_en_pares,
       coalesce(sum(nrt), 0)::bigint   AS det_nrt_en_pares
FROM pares;

\echo ''
\echo '=== V1.5 Completitud de calendar_weekly (53 semanas por celda) ==='
SELECT count(*)                                                AS filas,
       count(DISTINCT grid_id)                                 AS celdas,
       count(DISTINCT grid_id) * 53                            AS filas_esperadas,
       count(*) FILTER (WHERE n_data_years IS NULL)            AS n_data_years_null,
       count(*) FILTER (WHERE week_of_year = 53)               AS filas_semana_53
FROM calendar_weekly;

\echo ''
\echo '=== V1.6 Consistencia entre agregados ==='
SELECT
    (SELECT count(*) FROM grid_daily WHERE fire_day)                AS grid_daily_fire_days,
    (SELECT sum(fire_days) FROM grid_weekly)                        AS grid_weekly_fire_days,
    (SELECT count(*) FROM grid_weekly
      WHERE (fire_cells = 1) <> (fire_days > 0))                    AS inconsistentes_fire_cells,
    (SELECT count(*) FROM calendar_weekly
      WHERE n_fire_years > n_activity_years)                        AS inconsistentes_anios;

\echo ''
\echo '=== V1.7 Actividad semanal: absoluta y condicionada a deteccion ==='
SELECT week_of_year,
       sum(fire_cells)                                                AS celdas_con_fuego,
       sum(fire_days)                                                 AS dias_celda_con_fuego,
       sum(detection_days)                                            AS dias_con_deteccion,
       sum(n_detections)                                              AS detecciones,
       round(sum(fire_days)::numeric / NULLIF(sum(detection_days), 0), 4)
                                                                      AS fire_day_rate
FROM grid_weekly
GROUP BY 1
ORDER BY 1;

\echo ''
\echo '=== V1.8 Semantica de n_fire_years / n_activity_years ==='
SELECT 'celdas_semana_n_fire_years_ge7' AS metrica, count(*) AS valor
FROM calendar_weekly WHERE n_fire_years >= 7
UNION ALL
SELECT 'celdas_n_fire_years_ge7', count(*)
FROM (
    SELECT grid_id
    FROM calendar_weekly
    GROUP BY 1
    HAVING bool_or(n_fire_years >= 7)
) c
UNION ALL
SELECT 'celdas_total', count(DISTINCT grid_id) FROM calendar_weekly
UNION ALL
SELECT 'celdas_semana_n_activity_years_ge7', count(*)
FROM calendar_weekly WHERE n_activity_years >= 7;

SELECT n_fire_years, count(*) AS celdas_semana
FROM calendar_weekly
GROUP BY 1
ORDER BY 1;

\echo ''
\echo '=== V1.9 Mascara industrial por fuente ==='
SELECT source,
       count(*)                                  AS n,
       count(*) FILTER (WHERE industrial_mask)   AS industriales,
       round(100.0 * count(*) FILTER (WHERE industrial_mask) / count(*), 2) AS pct
FROM v_detections_analysis
GROUP BY 1
ORDER BY 1;

\echo ''
\echo '=== V1.10 Actividad y NRT por isoyear ==='
SELECT isoyear,
       count(*)                    AS celdas_semana,
       sum(fire_cells)             AS celdas_con_fuego,
       sum(fire_days)              AS fire_days,
       sum(has_nrt::int)           AS celdas_semana_con_nrt,
       round(avg(has_nrt::int), 3) AS share_nrt
FROM grid_weekly
GROUP BY 1
ORDER BY 1;

\echo ''
\echo '=== V1.11 Semana ISO 53 (pocos anios: no usar para anomalias sin n_ref) ==='
SELECT isoyear,
       count(*)        AS celdas_semana_53,
       sum(fire_cells) AS celdas_con_fuego
FROM grid_weekly
WHERE week_of_year = 53
GROUP BY 1
ORDER BY 1;

\echo ''
\echo '=== V1.12 Estado de cobertura ==='
SELECT count(*) AS filas_coverage_daily FROM coverage_daily;

\echo ''
\echo 'Fin de V1.'
