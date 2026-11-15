-- ============================================================================
-- Chronos Fire - Rebuild del calendario corregido (C2)
-- Base: chronos_fire
-- Requiere: grid_weekly, grid_weekly_sensor y calendar_weekly ya creadas.
-- Idempotente: se puede correr después de cada carga.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. grid_weekly
-- ----------------------------------------------------------------------------
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
-- 2. grid_weekly_sensor
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 3. calendar_weekly (53 semanas por celda)
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
ANALYZE grid_weekly_sensor;
ANALYZE calendar_weekly;

COMMIT;
