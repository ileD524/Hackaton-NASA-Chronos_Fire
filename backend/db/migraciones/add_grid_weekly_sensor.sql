-- ============================================================================
-- Chronos Fire - Actividad semanal por sensor
-- Necesario para el baseline SNPP-only (C0).
-- Base: chronos_fire
-- ============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS grid_weekly_sensor (
    grid_id        TEXT NOT NULL,
    cell_lat       DOUBLE PRECISION NOT NULL,
    cell_lon       DOUBLE PRECISION NOT NULL,
    isoyear        SMALLINT NOT NULL,
    week_of_year   SMALLINT NOT NULL,
    sensor         TEXT NOT NULL,
    fire_days      SMALLINT NOT NULL,
    fire_cells     SMALLINT NOT NULL,
    n_detections   INTEGER NOT NULL,
    frp_sum_mw     REAL,
    PRIMARY KEY (grid_id, isoyear, week_of_year, sensor)
);

COMMENT ON TABLE grid_weekly_sensor IS
    'Actividad semanal por celda, año ISO, semana ISO y sensor. Base para baseline SNPP-only.';

CREATE INDEX IF NOT EXISTS grid_weekly_sensor_sensor_week_idx
    ON grid_weekly_sensor (sensor, isoyear, week_of_year);

CREATE INDEX IF NOT EXISTS grid_weekly_sensor_latlon_idx
    ON grid_weekly_sensor (cell_lat, cell_lon);

TRUNCATE grid_weekly_sensor;

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
        frp_mw,
        (is_vegetation_effective IS TRUE
         OR (is_vegetation_effective IS NULL
             AND confidence_class IN ('nominal','high'))) AS veg_ok,
        confidence_class IN ('nominal','high') AS conf_ok
    FROM v_detections_analysis
),
cell_day_sensor AS (
    SELECT
        grid_id, cell_lat, cell_lon, isoyear, week_of_year, sensor, acq_date,
        count(*)::int AS n_detections,
        bool_or(veg_ok AND conf_ok) AS fire_day,
        sum(frp_mw) FILTER (WHERE veg_ok AND conf_ok)::real AS frp_sum
    FROM base
    GROUP BY 1, 2, 3, 4, 5, 6, 7
)
INSERT INTO grid_weekly_sensor (
    grid_id, cell_lat, cell_lon, isoyear, week_of_year, sensor,
    fire_days, fire_cells, n_detections, frp_sum_mw
)
SELECT
    grid_id, cell_lat, cell_lon, isoyear, week_of_year, sensor,
    count(*) FILTER (WHERE fire_day)::smallint AS fire_days,
    CASE WHEN count(*) FILTER (WHERE fire_day) > 0
         THEN 1 ELSE 0 END::smallint AS fire_cells,
    sum(n_detections)::int AS n_detections,
    sum(frp_sum)::real AS frp_sum_mw
FROM cell_day_sensor
GROUP BY 1, 2, 3, 4, 5, 6;

ANALYZE grid_weekly_sensor;

COMMIT;
