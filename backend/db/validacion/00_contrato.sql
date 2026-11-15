-- ============================================================================
-- Chronos Fire - Validacion §6.8
-- V0: contrato de validacion y registro de AOIs
-- Base: chronos_fire (PostgreSQL 17)
--
-- Idempotente: se puede correr varias veces.
--
-- Decisiones:
--   - El observado y el baseline usan el mismo sensor (era-especifica).
--   - Delta 2020: VIIRS_SNPP contra baseline VIIRS_SNPP 2013-2025.
--   - Eventos 2013: VIIRS_SNPP contra baseline VIIRS_SNPP 2013-2025
--     (leave-one-out). La tabla 6.7.1 usaba n_ref=9, compatible con un
--     baseline MODIS 2003-2011; eso mezclaba sensores y queda reemplazado.
--   - La Pampa: dos ventanas, 2016 semanas 52-53 y 2017 semanas 1-2.
--   - Las AOIs marcadas 'provisional' usan un bbox preliminar y deben
--     revisarse contra el evento documentado antes de citar resultados.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. AOIs de validacion
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS validation_aoi (
    aoi_id          TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    min_lat         DOUBLE PRECISION NOT NULL,
    min_lon         DOUBLE PRECISION NOT NULL,
    max_lat         DOUBLE PRECISION NOT NULL,
    max_lon         DOUBLE PRECISION NOT NULL,
    observed_sensor TEXT NOT NULL,
    baseline_sensor TEXT NOT NULL,
    ref_from        SMALLINT NOT NULL,
    ref_to          SMALLINT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'provisional',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT validation_aoi_bbox_ck CHECK (min_lat < max_lat AND min_lon < max_lon),
    CONSTRAINT validation_aoi_ref_ck CHECK (ref_from <= ref_to),
    CONSTRAINT validation_aoi_status_ck CHECK (status IN ('confirmed', 'provisional'))
);

COMMENT ON TABLE validation_aoi IS
    'AOIs congeladas para la validacion §6.8: bbox, sensor de observado, sensor de baseline y ventana de referencia.';
COMMENT ON COLUMN validation_aoi.observed_sensor IS
    'Sensor usado para calcular observed. Debe coincidir con baseline_sensor para comparar eras homogeneas.';
COMMENT ON COLUMN validation_aoi.baseline_sensor IS
    'Sensor usado para construir la climatologia de referencia.';
COMMENT ON COLUMN validation_aoi.ref_from IS
    'Primer isoyear de referencia (inclusive).';
COMMENT ON COLUMN validation_aoi.ref_to IS
    'Ultimo isoyear de referencia (inclusive).';
COMMENT ON COLUMN validation_aoi.status IS
    'confirmed = AOI verificada; provisional = bbox preliminar a revisar.';

-- ----------------------------------------------------------------------------
-- 2. Ventanas de evento (permite varias ventanas por AOI)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS validation_aoi_window (
    aoi_id        TEXT NOT NULL REFERENCES validation_aoi(aoi_id) ON DELETE CASCADE,
    isoyear       SMALLINT NOT NULL,
    week_from     SMALLINT NOT NULL,
    week_to       SMALLINT NOT NULL,
    expected_peak SMALLINT,
    PRIMARY KEY (aoi_id, isoyear, week_from, week_to),
    CONSTRAINT validation_aoi_window_weeks_ck CHECK (
        week_from BETWEEN 1 AND 53
        AND week_to BETWEEN 1 AND 53
        AND week_from <= week_to
    ),
    CONSTRAINT validation_aoi_window_peak_ck CHECK (
        expected_peak IS NULL
        OR expected_peak BETWEEN week_from AND week_to
    )
);

COMMENT ON TABLE validation_aoi_window IS
    'Ventanas isoyear/semana de cada evento, con el pico esperado.';

-- ----------------------------------------------------------------------------
-- 3. Seed de AOIs
-- ----------------------------------------------------------------------------
INSERT INTO validation_aoi (
    aoi_id, name, min_lat, min_lon, max_lat, max_lon,
    observed_sensor, baseline_sensor, ref_from, ref_to, status, notes
) VALUES
    (
        'delta_2020', 'Delta del Parana',
        -34.0, -61.0, -32.5, -59.5,
        'VIIRS_SNPP', 'VIIRS_SNPP', 2013, 2025, 'confirmed',
        'Reproduce 66/10/12/6.60 con SNPP-only. Con todos los sensores el observado es 78 y la anomalia 7.80.'
    ),
    (
        'corrientes_2022', 'Corrientes / Ibera',
        -29.5, -59.0, -27.5, -56.5,
        'VIIRS_SNPP', 'VIIRS_SNPP', 2013, 2025, 'provisional',
        'BBox preliminar. Recalcular la tabla 6.7.1 con este contrato.'
    ),
    (
        'salta_2013', 'Salta / Jujuy',
        -25.0, -65.0, -22.0, -63.0,
        'VIIRS_SNPP', 'VIIRS_SNPP', 2013, 2025, 'provisional',
        'BBox preliminar. La tabla 6.7.1 usaba n_ref=9, compatible con baseline MODIS 2003-2011; aqui se usa SNPP consistente.'
    ),
    (
        'cordoba_2013', 'Cordoba',
        -33.0, -65.0, -30.0, -63.0,
        'VIIRS_SNPP', 'VIIRS_SNPP', 2013, 2025, 'provisional',
        'BBox preliminar. La tabla 6.7.1 usaba n_ref=9, compatible con baseline MODIS 2003-2011; aqui se usa SNPP consistente.'
    ),
    (
        'la_pampa_2016_2017', 'La Pampa',
        -39.0, -68.0, -35.0, -63.0,
        'VIIRS_SNPP', 'VIIRS_SNPP', 2013, 2025, 'provisional',
        'BBox preliminar. Evento de dic-2016 a ene-2017: dos ventanas separadas.'
    )
ON CONFLICT (aoi_id) DO UPDATE SET
    name            = EXCLUDED.name,
    min_lat         = EXCLUDED.min_lat,
    min_lon         = EXCLUDED.min_lon,
    max_lat         = EXCLUDED.max_lat,
    max_lon         = EXCLUDED.max_lon,
    observed_sensor = EXCLUDED.observed_sensor,
    baseline_sensor = EXCLUDED.baseline_sensor,
    ref_from        = EXCLUDED.ref_from,
    ref_to          = EXCLUDED.ref_to,
    status          = EXCLUDED.status,
    notes           = EXCLUDED.notes;

-- Las ventanas del seed son canonicas: se reemplazan en cada corrida.
DELETE FROM validation_aoi_window
WHERE aoi_id IN (
    'delta_2020',
    'corrientes_2022',
    'salta_2013',
    'cordoba_2013',
    'la_pampa_2016_2017'
);

INSERT INTO validation_aoi_window (aoi_id, isoyear, week_from, week_to, expected_peak) VALUES
    ('delta_2020',           2020, 36, 42, 40),
    ('corrientes_2022',      2022,  2,  7,  3),
    ('salta_2013',           2013, 37, 37, 37),
    ('cordoba_2013',         2013, 37, 37, 37),
    ('la_pampa_2016_2017',   2016, 52, 53, 52),
    ('la_pampa_2016_2017',   2017,  1,  2,  1)
ON CONFLICT (aoi_id, isoyear, week_from, week_to) DO UPDATE SET
    expected_peak = EXCLUDED.expected_peak;

COMMIT;
