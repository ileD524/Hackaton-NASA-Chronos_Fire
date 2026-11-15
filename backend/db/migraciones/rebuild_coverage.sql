-- ============================================================================
-- n_data_years por celda/semana a partir de coverage_daily
-- Base: chronos_fire
-- Idempotente: se corre despues de populate_coverage.sql.
--
-- calendar_weekly es all-sensor. Para cada semana ISO se toma el maximo de
-- anios con cobertura entre los sensores disponibles; es un proxy regional
-- (region AR) y no reemplaza la cobertura real por nubosidad/overpass.
-- ============================================================================
BEGIN;

WITH per_sensor AS (
    SELECT sensor,
           extract(week FROM acq_date)::smallint AS week_of_year,
           count(DISTINCT extract(isoyear FROM acq_date))::int AS n_years
    FROM coverage_daily
    WHERE has_data
    GROUP BY 1, 2
),
coverage_week AS (
    SELECT week_of_year, max(n_years)::int AS n_data_years
    FROM per_sensor
    GROUP BY 1
)
UPDATE calendar_weekly cw
SET n_data_years = cw2.n_data_years
FROM coverage_week cw2
WHERE cw.week_of_year = cw2.week_of_year;

-- Semanas sin ninguna cobertura quedan en 0 (no en NULL).
UPDATE calendar_weekly
SET n_data_years = 0
WHERE n_data_years IS NULL;

ANALYZE calendar_weekly;

COMMIT;

-- Verificacion:
-- SELECT count(*) FILTER (WHERE n_data_years IS NULL) AS nulos,
--        min(n_data_years), max(n_data_years)
-- FROM calendar_weekly;
