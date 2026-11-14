-- ============================================================================
-- Chronos Fire - Modelo de datos
-- PostgreSQL 17
-- Base objetivo: chronos_fire
--
-- Crear la base antes de ejecutar este archivo:
--   CREATE DATABASE chronos_fire;
--   \c chronos_fire
--
-- Este esquema es la capa "curada": una fila por deteccion y agregados
-- precalculados para el calendario. La carga se hace con DuckDB (etl/load_pg.py).
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Detecciones harmonizadas (MODIS + VIIRS SNPP / NOAA-20 / NOAA-21)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS detections (
    sensor            TEXT NOT NULL,              -- MODIS | VIIRS_SNPP | VIIRS_NOAA20 | VIIRS_NOAA21
    source            TEXT NOT NULL,              -- SP (archive) | NRT
    product_version   TEXT,                       -- 2 | 6.03 | 61.03 | 6.1NRT | 2.0NRT
    satellite         TEXT,                       -- Terra | Aqua | SNPP | N20 | N21
    instrument        TEXT,                       -- MODIS | VIIRS (en SNPP FIRMS pone "SNPP")
    acquired_at       TIMESTAMPTZ NOT NULL,       -- fecha + hora UTC
    acq_date          DATE NOT NULL,
    acq_time_utc      SMALLINT,                   -- HHMM UTC
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    brightness        REAL,                       -- K, canal ~4 um
    bright_t31        REAL,                       -- K, canal ~11 um
    scan_km           REAL,                       -- tamano de pixel (scan)
    track_km          REAL,                       -- tamano de pixel (track)
    pixel_area_km2    REAL,                       -- scan_km * track_km
    frp_mw            REAL,                       -- Fire Radiative Power (MW)
    frp_density_wm2   REAL,                       -- frp_mw / pixel_area_km2
    confidence_raw    TEXT,                       -- 'l','n','h' o '0'..'100'
    confidence_norm   SMALLINT,                   -- 0..100 homologado
    confidence_class  TEXT,                       -- low | nominal | high
    daynight          CHAR(1),                    -- D | N
    fire_type         SMALLINT,                   -- 0 vegetacion, 1 volcan, 2 industrial, 3 offshore
    is_vegetation     BOOLEAN,                    -- fire_type = 0; NULL en NRT
    quality_flags     TEXT,                       -- CSV: frp_zero,low_brightness,industrial_or_offshore,bad_geometry
    harmonization_ver TEXT NOT NULL DEFAULT 'v1',
    ingested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT detections_pk PRIMARY KEY (sensor, source, latitude, longitude, acquired_at),
    CONSTRAINT detections_source_ck CHECK (source IN ('SP', 'NRT')),
    CONSTRAINT detections_confidence_ck CHECK (
        confidence_class IS NULL OR confidence_class IN ('low', 'nominal', 'high')
    )
);

COMMENT ON TABLE  detections IS 'Detecciones FIRMS harmonizadas: una fila por sensor/pixel/instante.';
COMMENT ON COLUMN detections.source IS 'SP = Standard Processing (archive); NRT = Near Real Time.';
COMMENT ON COLUMN detections.confidence_norm IS 'Confianza homologada 0-100: MODIS directa; VIIRS l=20, n=50, h=90.';
COMMENT ON COLUMN detections.frp_density_wm2 IS 'FRP por area del pixel, para comparar sensores de distinta resolucion.';
COMMENT ON COLUMN detections.quality_flags IS 'Flags separados por coma; NULL si no hay observaciones.';

CREATE INDEX IF NOT EXISTS detections_sensor_date_idx ON detections (sensor, acq_date);
CREATE INDEX IF NOT EXISTS detections_date_idx        ON detections (acq_date);
CREATE INDEX IF NOT EXISTS detections_latlon_idx      ON detections (latitude, longitude);
CREATE INDEX IF NOT EXISTS detections_type_idx        ON detections (fire_type);
CREATE INDEX IF NOT EXISTS detections_conf_idx        ON detections (confidence_class);
CREATE INDEX IF NOT EXISTS detections_acquired_brin   ON detections USING BRIN (acquired_at);
-- Indice de apoyo para la politica SP/NRT (vista v_detections_canonical)
CREATE INDEX IF NOT EXISTS detections_sensor_source_date_idx ON detections (sensor, source, acq_date);

-- Opcional si se instala PostGIS:
-- CREATE EXTENSION IF NOT EXISTS postgis;
-- ALTER TABLE detections ADD COLUMN IF NOT EXISTS geom geometry(Point, 4326)
--     GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)) STORED;
-- CREATE INDEX IF NOT EXISTS detections_geom_gix ON detections USING GIST (geom);

-- ----------------------------------------------------------------------------
-- 2. Agregado diario por celda (0.05 grados ~ 5 km)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grid_daily (
    grid_id        TEXT NOT NULL,                 -- '0.05:<lat_idx>:<lon_idx>'
    cell_lat       DOUBLE PRECISION NOT NULL,     -- centro de celda
    cell_lon       DOUBLE PRECISION NOT NULL,
    day            DATE NOT NULL,
    n_detections   INTEGER NOT NULL,
    fire_day       BOOLEAN NOT NULL,              -- hubo fuego de vegetacion confiable
    frp_sum_mw     REAL,
    frp_max_mw     REAL,
    sensors        TEXT[],                        -- sensores que detectaron ese dia/celda
    confidence_max SMALLINT,
    CONSTRAINT grid_daily_pk PRIMARY KEY (grid_id, day)
);

COMMENT ON TABLE grid_daily IS 'Agregado diario por celda de 0.05 grados; base del calendario.';
COMMENT ON COLUMN grid_daily.fire_day IS 'TRUE si al menos una deteccion de vegetacion con confianza nominal/alta cae en la celda/dia.';

CREATE INDEX IF NOT EXISTS grid_daily_day_idx    ON grid_daily (day);
CREATE INDEX IF NOT EXISTS grid_daily_latlon_idx ON grid_daily (cell_lat, cell_lon);
CREATE INDEX IF NOT EXISTS grid_daily_fire_idx   ON grid_daily (grid_id, day) WHERE fire_day;

-- ----------------------------------------------------------------------------
-- 3. Climatologia semanal por celda (calendario historico)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS calendar_weekly (
    grid_id       TEXT NOT NULL,
    cell_lat      DOUBLE PRECISION NOT NULL,
    cell_lon      DOUBLE PRECISION NOT NULL,
    week_of_year  SMALLINT NOT NULL,              -- ISO week 1..53
    n_years       INTEGER,
    fire_day_prob REAL,                           -- probabilidad historica de dia con fuego
    frp_p50       REAL,
    frp_p90       REAL,
    CONSTRAINT calendar_weekly_pk PRIMARY KEY (grid_id, week_of_year)
);

COMMENT ON TABLE calendar_weekly IS 'Climatologia por celda y semana del anio; insumo de anomalias y calendario.';

CREATE INDEX IF NOT EXISTS calendar_weekly_week_idx   ON calendar_weekly (week_of_year);
CREATE INDEX IF NOT EXISTS calendar_weekly_latlon_idx ON calendar_weekly (cell_lat, cell_lon);

-- ----------------------------------------------------------------------------
-- 4. Trazabilidad de cargas (opcional, la usa el ETL)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ingest_runs (
    run_id       BIGSERIAL PRIMARY KEY,
    dataset      TEXT NOT NULL,                   -- SV_ARCHIVE, J2V_NRT, etc.
    sensor       TEXT NOT NULL,
    source       TEXT NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at  TIMESTAMPTZ,
    rows_loaded  BIGINT NOT NULL DEFAULT 0,
    status       TEXT NOT NULL DEFAULT 'running',
    notes        TEXT,
    CONSTRAINT ingest_runs_status_ck CHECK (status IN ('running', 'ok', 'error'))
);

-- ----------------------------------------------------------------------------
-- 5. Vistas de consumo
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_latest_nrt AS
SELECT *
FROM detections
WHERE source = 'NRT'
  AND acquired_at >= now() - INTERVAL '7 days'
  AND confidence_class IN ('nominal', 'high')
  AND (is_vegetation IS NULL OR is_vegetation);

COMMENT ON VIEW v_latest_nrt IS 'Alertas NRT de los ultimos 7 dias, sin focos industriales conocidos.';

CREATE OR REPLACE VIEW v_vegetation_detections AS
SELECT *
FROM detections
WHERE (is_vegetation IS TRUE OR (is_vegetation IS NULL AND confidence_class IN ('nominal', 'high')));

COMMENT ON VIEW v_vegetation_detections IS 'Detecciones aptas para analisis de vegetacion (incluye NRT con confianza nominal/alta).';

-- Politica SP/NRT: para cada sensor + fecha, SP si existe; NRT si no existe SP.
CREATE OR REPLACE VIEW v_detections_canonical AS
SELECT d.*
FROM detections d
WHERE d.source = 'SP'
   OR NOT EXISTS (
       SELECT 1
       FROM detections s
       WHERE s.sensor   = d.sensor
         AND s.source   = 'SP'
         AND s.acq_date = d.acq_date
   );

COMMENT ON VIEW v_detections_canonical IS 'Politica SP/NRT: SP si existe para sensor/fecha; NRT si no existe SP.';

COMMIT;
