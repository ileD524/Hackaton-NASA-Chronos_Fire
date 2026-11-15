-- ============================================================================
-- Chronos Fire - Corrección del calendario (C1 + C2)
-- Base: chronos_fire
--
-- Decisiones C0:
--   - Titular: fire_cells (no n_detections)
--   - Baseline por AOI: observado vs mediana multianual
--   - Eras: MODIS 2003-2011; VIIRS SNPP 2013-2025
--   - Excluir 2000-2002, 2012, 2026 del baseline
--   - n_ref_years >= 7 (alta confianza >= 10)
--   - baseline >= 3 celdas; observado >= 3
--   - Mediana, no media
--   - isoyear en todo el calendario
--   - 53 semanas por celda
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 0. Backup de la tabla vieja
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS calendar_weekly_legacy AS TABLE calendar_weekly;

-- ----------------------------------------------------------------------------
-- 1. Renombrar columnas viejas
-- ----------------------------------------------------------------------------
ALTER TABLE calendar_weekly
    RENAME COLUMN fire_day_prob TO fire_day_rate_given_detection;

ALTER TABLE calendar_weekly
    RENAME COLUMN n_years TO n_fire_years;

-- ----------------------------------------------------------------------------
-- 2. Nuevas columnas
-- ----------------------------------------------------------------------------
ALTER TABLE calendar_weekly
    ADD COLUMN IF NOT EXISTS n_data_years INTEGER,
    ADD COLUMN IF NOT EXISTS n_activity_years INTEGER,
    ADD COLUMN IF NOT EXISTS fire_days_mean REAL,
    ADD COLUMN IF NOT EXISTS detection_days_mean REAL,
    ADD COLUMN IF NOT EXISTS provisional_share REAL;

COMMENT ON COLUMN calendar_weekly.fire_day_rate_given_detection IS
    'Tasa de fire_day condicionada a detecciones. NO es probabilidad de fuego.';
COMMENT ON COLUMN calendar_weekly.n_fire_years IS
    'Años con fire_day=1 para esa celda/semana.';
COMMENT ON COLUMN calendar_weekly.n_data_years IS
    'Años con cobertura de datos. NULL hasta cargar coverage_daily.';
COMMENT ON COLUMN calendar_weekly.n_activity_years IS
    'Años con al menos una detección en la celda/semana.';
COMMENT ON COLUMN calendar_weekly.fire_days_mean IS
    'Promedio de días con fire_day por año con actividad.';
COMMENT ON COLUMN calendar_weekly.detection_days_mean IS
    'Promedio de días con detecciones por año con actividad.';
COMMENT ON COLUMN calendar_weekly.provisional_share IS
    'Proporción de años con NRT para esa celda/semana.';

-- ----------------------------------------------------------------------------
-- 3. Cobertura (esquema; se llena cuando haya data_availability/missing_data)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS coverage_daily (
    region        TEXT NOT NULL DEFAULT 'AR',
    sensor        TEXT NOT NULL,
    source        TEXT NOT NULL,
    acq_date      DATE NOT NULL,
    has_data      BOOLEAN NOT NULL DEFAULT TRUE,
    coverage_type TEXT NOT NULL DEFAULT 'proxy',
    PRIMARY KEY (region, sensor, source, acq_date)
);

COMMENT ON TABLE coverage_daily IS
    'Cobertura por region/sensor/fecha. Proxy: pendiente de data_availability de FIRMS.';

-- ----------------------------------------------------------------------------
-- 4. grid_weekly: actividad observada por celda / isoyear / semana
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grid_weekly (
    grid_id              TEXT NOT NULL,
    cell_lat             DOUBLE PRECISION NOT NULL,
    cell_lon             DOUBLE PRECISION NOT NULL,
    isoyear              SMALLINT NOT NULL,
    week_of_year         SMALLINT NOT NULL,
    fire_days            SMALLINT NOT NULL,
    fire_cells           SMALLINT NOT NULL,
    n_detections         INTEGER NOT NULL,
    detection_days       SMALLINT NOT NULL,
    detections_by_sensor JSONB,
    frp_by_sensor        JSONB,
    has_sp               BOOLEAN NOT NULL DEFAULT FALSE,
    has_nrt              BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (grid_id, isoyear, week_of_year)
);

COMMENT ON TABLE grid_weekly IS
    'Actividad semanal observada por celda, año ISO y semana ISO.';
COMMENT ON COLUMN grid_weekly.fire_cells IS
    '1 si hubo al menos un fire_day en la celda/semana; 0 si no.';
COMMENT ON COLUMN grid_weekly.detections_by_sensor IS
    'Conteo de detecciones por sensor; evita sumar sensores.';
COMMENT ON COLUMN grid_weekly.frp_by_sensor IS
    'FRP de vegetación confiable por sensor; evita sumar FRP entre sensores.';

CREATE INDEX IF NOT EXISTS grid_weekly_year_week_idx
    ON grid_weekly (isoyear, week_of_year);

CREATE INDEX IF NOT EXISTS grid_weekly_latlon_idx
    ON grid_weekly (cell_lat, cell_lon);

TRUNCATE grid_weekly;

WITH base AS (
    SELECT
        '0.05:' || CAST(round(latitude / 0.05) AS BIGINT) || ':' ||
                   CAST(round(longitude / 0.05) AS BIGINT) AS grid_id,
        round(latitude / 0.05) * 0.05  AS cell_lat,
        round(longitude / 0.05) * 0.05 AS cell_lon,
        acq_date,
        extract(isoyear FROM acq_date)::smallint AS isoyear,
        extract(week FROM acq_date)::smallint    AS week_of_year,
        sensor,
        source,
        frp_mw,
        (is_vegetation_effective IS TRUE
         OR (is_vegetation_effective IS NULL
             AND confidence_class IN ('nominal','high'))) AS veg_ok,
        confidence_class IN ('nominal','high') AS conf_ok
    FROM v_detections_analysis
),
cell_day AS (
    SELECT
        grid_id, cell_lat, cell_lon, acq_date, isoyear, week_of_year,
        count(*)::int AS n_detections,
        bool_or(veg_ok AND conf_ok) AS fire_day,
        bool_or(source = 'SP')  AS has_sp,
        bool_or(source = 'NRT') AS has_nrt
    FROM base
    GROUP BY 1, 2, 3, 4, 5, 6
),
weekly AS (
    SELECT
        grid_id, cell_lat, cell_lon, isoyear, week_of_year,
        count(*) FILTER (WHERE fire_day)::smallint AS fire_days,
        CASE WHEN count(*) FILTER (WHERE fire_day) > 0
             THEN 1 ELSE 0 END::smallint AS fire_cells,
        sum(n_detections)::int AS n_detections,
        count(*)::smallint AS detection_days,
        bool_or(has_sp)  AS has_sp,
        bool_or(has_nrt) AS has_nrt
    FROM cell_day
    GROUP BY 1, 2, 3, 4, 5
),
sensor_weekly AS (
    SELECT
        grid_id, isoyear, week_of_year,
        jsonb_object_agg(sensor, n_det) AS detections_by_sensor,
        jsonb_object_agg(sensor, frp_sum) AS frp_by_sensor
    FROM (
        SELECT
            grid_id, isoyear, week_of_year, sensor,
            count(*)::int AS n_det,
            sum(frp_mw) FILTER (WHERE veg_ok AND conf_ok)::real AS frp_sum
        FROM base
        GROUP BY 1, 2, 3, 4
    ) s
    GROUP BY 1, 2, 3
)
INSERT INTO grid_weekly (
    grid_id, cell_lat, cell_lon, isoyear, week_of_year,
    fire_days, fire_cells, n_detections, detection_days,
    detections_by_sensor, frp_by_sensor, has_sp, has_nrt
)
SELECT
    w.grid_id, w.cell_lat, w.cell_lon, w.isoyear, w.week_of_year,
    w.fire_days, w.fire_cells, w.n_detections, w.detection_days,
    s.detections_by_sensor, s.frp_by_sensor, w.has_sp, w.has_nrt
FROM weekly w
LEFT JOIN sensor_weekly s
  ON s.grid_id = w.grid_id
 AND s.isoyear = w.isoyear
 AND s.week_of_year = w.week_of_year;

-- ----------------------------------------------------------------------------
-- 5. calendar_weekly: climatología con 53 semanas por celda
-- ----------------------------------------------------------------------------
TRUNCATE calendar_weekly;

WITH grids AS (
    SELECT DISTINCT grid_id, cell_lat, cell_lon
    FROM grid_weekly
),
weeks AS (
    SELECT generate_series(1, 53)::smallint AS week_of_year
),
obs AS (
    SELECT
        grid_id,
        week_of_year,
        count(DISTINCT isoyear) FILTER (WHERE fire_cells = 1)::int AS n_fire_years,
        count(DISTINCT isoyear)::int AS n_activity_years,
        avg(fire_days)::real AS fire_days_mean,
        avg(detection_days)::real AS detection_days_mean,
        avg(has_nrt::int)::real AS provisional_share,
        sum(fire_days)::real / NULLIF(sum(detection_days), 0) AS fire_day_rate_given_detection
    FROM grid_weekly
    GROUP BY 1, 2
)
INSERT INTO calendar_weekly (
    grid_id, cell_lat, cell_lon, week_of_year,
    n_fire_years, n_data_years, n_activity_years,
    fire_day_rate_given_detection,
    fire_days_mean, detection_days_mean, provisional_share,
    frp_p50, frp_p90
)
SELECT
    g.grid_id,
    g.cell_lat,
    g.cell_lon,
    w.week_of_year,
    COALESCE(o.n_fire_years, 0),
    NULL::int AS n_data_years,
    COALESCE(o.n_activity_years, 0),
    o.fire_day_rate_given_detection,
    o.fire_days_mean,
    o.detection_days_mean,
    o.provisional_share,
    NULL::real AS frp_p50,
    NULL::real AS frp_p90
FROM grids g
CROSS JOIN weeks w
LEFT JOIN obs o
  ON o.grid_id = g.grid_id
 AND o.week_of_year = w.week_of_year;

ANALYZE grid_weekly;
ANALYZE calendar_weekly;

COMMIT;
