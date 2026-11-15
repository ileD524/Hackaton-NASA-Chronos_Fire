-- ============================================================================
-- Cobertura diaria (proxy documentado)
-- Base: chronos_fire
-- Idempotente: se puede correr despues de cada carga del ETL.
--
-- No hay data_availability oficial de FIRMS en el repositorio. Como proxy se
-- usa cada combinacion sensor/fuente/fecha con al menos una deteccion. Es una
-- cota inferior: un dia sin detecciones queda como "sin cobertura", no como
-- "sin fuego". El tipo queda identificado en coverage_type = 'proxy'.
-- ============================================================================
BEGIN;

CREATE INDEX IF NOT EXISTS coverage_daily_sensor_date_idx
    ON coverage_daily (sensor, acq_date);

INSERT INTO coverage_daily (region, sensor, source, acq_date, has_data, coverage_type)
SELECT DISTINCT 'AR', sensor, source, acq_date, true, 'proxy'
FROM detections
ON CONFLICT (region, sensor, source, acq_date) DO NOTHING;

COMMIT;

-- Verificacion:
-- SELECT sensor, source, count(*) AS dias_con_datos, min(acq_date), max(acq_date)
-- FROM coverage_daily
-- WHERE has_data
-- GROUP BY 1, 2
-- ORDER BY 1, 2;
