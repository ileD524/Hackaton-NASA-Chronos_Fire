-- ============================================================================
-- Chronos Fire - Cobertura diaria (proxy documentado)
-- Fase 0 de PLAN_RIESGO_ESTADISTICO.md
-- Base: chronos_fire
-- Idempotente: se puede correr despues de cada carga del ETL.
--
-- No hay data_availability oficial de FIRMS en el repositorio. Como proxy se
-- usa cada combinacion sensor/fuente/fecha con al menos una deteccion. Es una
-- cota inferior: un dia sin detecciones queda como "sin cobertura", no como
-- "sin fuego". El tipo queda identificado en coverage_type = 'proxy'.
--
-- Volumen esperado: la PK es (region, sensor, source, acq_date), asi que el
-- total son las combinaciones distintas de `detections` (~19.260 filas), no
-- una fila por dia y sensor. Ver backend/db/README.md.
-- ============================================================================
BEGIN;

CREATE INDEX IF NOT EXISTS coverage_daily_sensor_date_idx
    ON coverage_daily (sensor, acq_date);

INSERT INTO coverage_daily (region, sensor, source, acq_date, has_data, coverage_type)
SELECT DISTINCT 'AR', sensor, source, acq_date, true, 'proxy'
FROM detections
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
