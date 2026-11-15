-- ============================================================================
-- Chronos Fire - Construccion de la mascara de focos industriales
-- Base: chronos_fire
-- Requiere: tabla industrial_cells y detections cargada.
--
-- Regla:
--   - Solo SP (donde existe type)
--   - Solo type = 2 (fuentes industriales/estaticas)
--   - Grilla 0.01 grados
--   - n_industrial >= 10
--   - n_years >= 3
--   - industrial_ratio >= 0.5
--   - Sin dilatacion
-- ============================================================================

BEGIN;

TRUNCATE industrial_cells;

INSERT INTO industrial_cells (
    cell_lat_idx, cell_lon_idx, cell_lat, cell_lon,
    n_industrial, n_total, industrial_ratio, n_years,
    first_seen, last_seen, types, mask_version
)
WITH base AS (
    SELECT
        round(latitude / 0.01)::int  AS cell_lat_idx,
        round(longitude / 0.01)::int AS cell_lon_idx,
        round(latitude / 0.01)::int * 0.01  AS cell_lat,
        round(longitude / 0.01)::int * 0.01 AS cell_lon,
        count(*) FILTER (WHERE fire_type = 2) AS n_industrial,
        count(*) AS n_total,
        count(DISTINCT extract(year FROM acq_date))
            FILTER (WHERE fire_type = 2) AS n_years,
        min(acq_date) FILTER (WHERE fire_type = 2) AS first_seen,
        max(acq_date) FILTER (WHERE fire_type = 2) AS last_seen,
        array_agg(DISTINCT fire_type)
            FILTER (WHERE fire_type = 2) AS types
    FROM detections
    WHERE source = 'SP'
    GROUP BY 1, 2, 3, 4
)
SELECT
    cell_lat_idx, cell_lon_idx, cell_lat, cell_lon,
    n_industrial, n_total,
    n_industrial::real / NULLIF(n_total, 0) AS industrial_ratio,
    n_years, first_seen, last_seen, types, 'v1'
FROM base
WHERE n_industrial >= 10
  AND n_years >= 3
  AND n_industrial::real / NULLIF(n_total, 0) >= 0.5;

ANALYZE industrial_cells;

COMMIT;

-- Verificacion rapida:
-- SELECT count(*) FROM industrial_cells;
-- SELECT * FROM industrial_cells ORDER BY n_industrial DESC LIMIT 20;
