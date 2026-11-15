-- ============================================================================
-- Chronos Fire - Cobertura diaria (denominador del riesgo estadistico)
-- Fase 0 de PLAN_RIESGO_ESTADISTICO.md
-- Base: chronos_fire
-- Requiere: coverage_daily (ya existe, ver migraciones/fix_calendar.sql)
-- Idempotente: se puede correr despues de cada carga del ETL.
--
-- Que hace:
--   Marca un dia como cubierto si el sensor registro al menos una deteccion en
--   la region. Es un proxy regional (coverage_type = 'proxy'), no prueba de
--   ausencia de nubes: el riesgo calculado con este denominador es una cota
--   inferior.
--
-- Nota de volumen: la PK es (region, sensor, source, acq_date), asi que el
-- total esperado es el numero de combinaciones distintas
-- (sensor, source, acq_date) de `detections`, hoy ~19.260 filas, no 200.000.
-- Ver backend/db/README.md, seccion "Desvios respecto del plan".
-- ============================================================================

BEGIN;

INSERT INTO coverage_daily (region, sensor, source, acq_date, has_data, coverage_type)
SELECT 'AR', d.sensor, d.source, d.acq_date, true, 'proxy'
FROM (SELECT DISTINCT sensor, source, acq_date FROM detections) d
ON CONFLICT (region, sensor, source, acq_date) DO NOTHING;

COMMIT;

-- ----------------------------------------------------------------------------
-- Verificacion 1: total y rango temporal por sensor/source
-- ----------------------------------------------------------------------------
\echo ''
\echo '=== C1.1 Cobertura cargada por sensor y source ==='
SELECT sensor, source,
       count(*)                                   AS dias_cubiertos,
       min(acq_date)                              AS desde,
       max(acq_date)                              AS hasta
FROM coverage_daily
GROUP BY 1, 2
ORDER BY 1, 2;

-- ----------------------------------------------------------------------------
-- Verificacion 2: dias cubiertos por sensor y anio (debe dar ~365)
-- ----------------------------------------------------------------------------
\echo ''
\echo '=== C1.2 Dias cubiertos por sensor y anio (esperado ~365) ==='
WITH dias AS (
    SELECT sensor, extract(year FROM acq_date)::int AS anio,
           count(DISTINCT acq_date) AS dias_cubiertos
    FROM coverage_daily
    WHERE has_data
    GROUP BY 1, 2
), esperado AS (
    SELECT anio,
           (make_date(anio, 12, 31) - make_date(anio, 1, 1) + 1) AS dias_anio
    FROM generate_series(2000, 2026) AS anio
)
SELECT d.sensor, d.anio, d.dias_cubiertos, e.dias_anio,
       round(100.0 * d.dias_cubiertos / e.dias_anio, 1) AS pct
FROM dias d
JOIN esperado e USING (anio)
ORDER BY d.sensor, d.anio;

-- ----------------------------------------------------------------------------
-- Verificacion 3: pares (era, anio ISO, semana ISO) esperados vs cubiertos
-- El calendario ISO se genera dia por dia dentro de la ventana de cada era,
-- para no exigir semana 53 en anios que no la tienen.
-- Debe devolver 0 filas.
-- ----------------------------------------------------------------------------
\echo ''
\echo '=== C1.3 Pares era/anio/semana sin cobertura (0 filas = OK) ==='
WITH pares AS (
    SELECT DISTINCT 'MODIS'::text AS era,
           extract(isoyear FROM d)::int      AS isoyear,
           extract(week    FROM d)::smallint AS week_of_year
    FROM generate_series('2003-01-01'::date, '2011-12-31'::date, '1 day') AS d
    UNION ALL
    SELECT DISTINCT 'SNPP'::text,
           extract(isoyear FROM d)::int,
           extract(week    FROM d)::smallint
    FROM generate_series('2013-01-01'::date, '2025-12-31'::date, '1 day') AS d
),
cov AS (
    SELECT DISTINCT
           CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           extract(isoyear FROM acq_date)::int      AS isoyear,
           extract(week    FROM acq_date)::smallint AS week_of_year
    FROM coverage_daily
    WHERE has_data
      AND sensor IN ('MODIS', 'VIIRS_SNPP')
)
SELECT p.era, p.isoyear, p.week_of_year
FROM pares p
LEFT JOIN cov c USING (era, isoyear, week_of_year)
WHERE c.era IS NULL
ORDER BY 1, 2, 3;

-- ----------------------------------------------------------------------------
-- Verificacion 4: anios de referencia efectivos por era y semana ISO.
-- Esperado: MODIS = 9 en semanas 1-52 y 2 en la 53;
--           SNPP  = 13 en semanas 1-52 y 2 en la 53.
-- Un valor en 0 seria un hueco.
-- ----------------------------------------------------------------------------
\echo ''
\echo '=== C1.4 Anios de referencia (n_ref) por era y semana ISO ==='
SELECT era,
       count(*)                  AS semanas,
       min(ref_years)            AS n_ref_min,
       max(ref_years)            AS n_ref_max,
       count(*) FILTER (WHERE ref_years = 0) AS semanas_sin_cobertura
FROM (
    SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
           extract(week FROM acq_date)::smallint AS week_of_year,
           count(DISTINCT extract(isoyear FROM acq_date))::int AS ref_years
    FROM coverage_daily
    WHERE has_data
      AND sensor IN ('MODIS', 'VIIRS_SNPP')
      AND (
            (sensor = 'MODIS'      AND extract(isoyear FROM acq_date) BETWEEN 2003 AND 2011)
         OR (sensor = 'VIIRS_SNPP' AND extract(isoyear FROM acq_date) BETWEEN 2013 AND 2025)
      )
    GROUP BY 1, 2
) t
GROUP BY 1
ORDER BY 1;

\echo ''
\echo '=== C1.5 Detalle de n_ref para la semana 53 (esperado 2 por era) ==='
SELECT CASE WHEN sensor = 'MODIS' THEN 'MODIS' ELSE 'SNPP' END AS era,
       count(DISTINCT extract(isoyear FROM acq_date))::int AS ref_years
FROM coverage_daily
WHERE has_data
  AND extract(week FROM acq_date) = 53
  AND (
        (sensor = 'MODIS'      AND extract(isoyear FROM acq_date) BETWEEN 2003 AND 2011)
     OR (sensor = 'VIIRS_SNPP' AND extract(isoyear FROM acq_date) BETWEEN 2013 AND 2025)
  )
GROUP BY 1
ORDER BY 1;
