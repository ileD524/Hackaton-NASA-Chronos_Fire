-- ============================================================================
-- Chronos Fire - Capa de riesgo estadistico (DDL)
-- Fases 0 y 1 de PLAN_RIESGO_ESTADISTICO.md
-- Base: chronos_fire (PostgreSQL 17)
-- Idempotente: CREATE ... IF NOT EXISTS, se puede correr varias veces.
--
-- No toca ninguna tabla existente. Solo crea:
--   risk_prior_week   (prior Beta por era y semana ISO)
--   risk_cell_week    (riesgo materializado por celda-semana)
--   risk_backtest     (calibracion, se llena en la fase 4)
--   indice grid_weekly_sensor (grid_id, week_of_year) para alertas
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Prior empirico por era y semana ISO
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_prior_week (
    era           TEXT     NOT NULL,          -- 'MODIS' | 'SNPP'
    week_of_year  SMALLINT NOT NULL,
    mu            REAL     NOT NULL,          -- tasa AOI de la semana
    kappa         REAL     NOT NULL,          -- fuerza del prior
    alpha         REAL     NOT NULL,          -- mu * kappa
    beta          REAL     NOT NULL,          -- (1 - mu) * kappa
    PRIMARY KEY (era, week_of_year)
);

COMMENT ON TABLE risk_prior_week IS
    'Prior Beta-Binomial por era y semana ISO. mu = celdas-anio con fuego / celdas-anio con cobertura.';
COMMENT ON COLUMN risk_prior_week.mu IS
    'Tasa AOI de la semana: celdas-anio con fire_days > 0 sobre celdas-anio con cobertura.';
COMMENT ON COLUMN risk_prior_week.kappa IS
    'Fuerza del prior (8 = debil): el dato local domina a partir de 3-4 anios con fuego.';

-- ----------------------------------------------------------------------------
-- 2. Riesgo por celda y semana ISO
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_cell_week (
    grid_id       TEXT     NOT NULL,
    cell_lat      DOUBLE PRECISION NOT NULL,
    cell_lon      DOUBLE PRECISION NOT NULL,
    week_of_year  SMALLINT NOT NULL,
    era           TEXT     NOT NULL,          -- 'MODIS' | 'SNPP'
    ref_years     INTEGER  NOT NULL,          -- n: anios con cobertura
    fire_years    INTEGER  NOT NULL,          -- k: anios con fire_days > 0
    fire_days     INTEGER  NOT NULL,          -- intensidad acumulada en la era
    p_fire        REAL     NOT NULL,          -- media posterior suavizada
    p_lo          REAL     NOT NULL,          -- Wilson 90% sobre k/n
    p_hi          REAL     NOT NULL,          -- Wilson 90% sobre k/n
    det_p50       REAL,                       -- n_detections semanal, p50 historico
    det_p90       REAL,                       -- n_detections semanal, p90 historico
    reliability   TEXT     NOT NULL,          -- high | low | insufficient
    model_version TEXT     NOT NULL,
    built_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (grid_id, week_of_year, era)
);

COMMENT ON TABLE risk_cell_week IS
    'Frecuencia historica estimada de actividad de fuego por celda 0.05 y semana ISO. No es prediccion ni riesgo oficial.';
COMMENT ON COLUMN risk_cell_week.p_fire IS
    'Media posterior Beta-Binomial: (k + alpha) / (n + alpha + beta).';
COMMENT ON COLUMN risk_cell_week.p_lo IS
    'Limite inferior del intervalo de Wilson al 90% sobre la proporcion cruda k/n.';
COMMENT ON COLUMN risk_cell_week.p_hi IS
    'Limite superior del intervalo de Wilson al 90% sobre la proporcion cruda k/n.';
COMMENT ON COLUMN risk_cell_week.det_p50 IS
    'Percentil 50 de n_detections semanal en los anios de referencia con actividad.';
COMMENT ON COLUMN risk_cell_week.reliability IS
    'high: n >= 10; low: 5 <= n < 10; insufficient: n < 5. La UI no debe mostrar p sin n ni intervalo.';

CREATE INDEX IF NOT EXISTS risk_cell_week_week_idx
    ON risk_cell_week (week_of_year, era);
CREATE INDEX IF NOT EXISTS risk_cell_week_latlon_idx
    ON risk_cell_week (cell_lat, cell_lon);

-- ----------------------------------------------------------------------------
-- 3. Calibracion publicada (fase 4)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_backtest (
    model_version  TEXT     NOT NULL,
    era            TEXT     NOT NULL,
    holdout_year   SMALLINT NOT NULL,
    brier          REAL,
    brier_baseline REAL,
    bss            REAL,
    auc            REAL,
    n              BIGINT,
    built_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (model_version, era, holdout_year)
);

COMMENT ON TABLE risk_backtest IS
    'Metricas de calibracion por holdout temporal (Brier, BSS, AUC). Se publica recien en la fase 4.';

-- ----------------------------------------------------------------------------
-- 4. Indice para el calculo por alerta (celda + semana ISO)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS grid_weekly_sensor_cell_week_idx
    ON grid_weekly_sensor (grid_id, week_of_year);

COMMIT;
