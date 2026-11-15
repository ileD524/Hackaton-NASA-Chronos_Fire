-- ============================================================================
-- Smoke test de datos: Delta del Parana, 2020, semana ISO 40, VIIRS SNPP.
--
-- Esperado: observed = 66, baseline = 10, n_ref = 12, anomaly = 6.60.
-- Es el caso estrella de CONTRATO_API.md y VALIDACION_6_8.md.
--
-- Uso:
--   psql -h 127.0.0.1 -U postgres -d chronos_fire -v ON_ERROR_STOP=1 \
--     -f backend/db/validacion/05_smoke_delta_2020.sql
--
-- Solo lectura. No crea objetos ni modifica datos.
-- ============================================================================

DO $$
DECLARE
    observed  integer;
    baseline  numeric;
    n_ref     integer;
    anomaly   numeric;
BEGIN
    SELECT coalesce(sum(fire_cells), 0)::int
    INTO observed
    FROM grid_weekly_sensor
    WHERE sensor = 'VIIRS_SNPP'
      AND isoyear = 2020
      AND week_of_year = 40
      AND cell_lat BETWEEN -34 AND -32.5
      AND cell_lon BETWEEN -61 AND -59.5;

    SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY n), count(*)::int
    INTO baseline, n_ref
    FROM (
        SELECT isoyear, sum(fire_cells)::numeric AS n
        FROM grid_weekly_sensor
        WHERE sensor = 'VIIRS_SNPP'
          AND week_of_year = 40
          AND isoyear BETWEEN 2013 AND 2025
          AND isoyear <> 2020
          AND cell_lat BETWEEN -34 AND -32.5
          AND cell_lon BETWEEN -61 AND -59.5
        GROUP BY isoyear
    ) yearly;

    anomaly := round(observed / baseline, 2);

    IF observed <> 66 OR baseline <> 10 OR n_ref <> 12 OR anomaly <> 6.60 THEN
        RAISE EXCEPTION
            'Delta 2020 semana 40: esperado 66/10/12/6.60, obtenido %/%/%/%',
            observed, baseline, n_ref, anomaly;
    END IF;

    RAISE NOTICE 'Delta 2020 semana 40 OK: % / % / % / %',
        observed, baseline, n_ref, anomaly;
END $$;
