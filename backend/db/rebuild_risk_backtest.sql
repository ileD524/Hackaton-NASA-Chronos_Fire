-- ============================================================================
-- Chronos Fire - Backtesting de la capa de riesgo (fase 4)
-- Base: chronos_fire (PostgreSQL 17)
--
-- Entrena con una ventana temporal y evalua en un anio holdout, usando solo
-- datos de entrenamiento. No usa risk_cell_week para predecir: recalcula
-- k, n y el prior con la ventana de entrenamiento para no filtrar el fututo.
--
-- Metricas: Brier, Brier Skill Score contra mu_w, AUC y curva de fiabilidad
-- por decil. Persiste Brier/BSS/AUC/n en risk_backtest.
--
-- Uso (desde la raiz del repo):
--   & psql ... -v ON_ERROR_STOP=1 -f backend\db\rebuild_risk_backtest.sql
--   & psql ... -v ON_ERROR_STOP=1 -v model_version=risk-v1-t2019-2024 `
--       -v train_from=2019 -v train_to=2024 `
--       -f backend\db\rebuild_risk_backtest.sql
--
-- Variables (con defaults):
--   model_version  etiqueta del modelo/ventana (default risk-v1)
--   train_from     primer anio de entrenamiento (default 2013)
--   train_to       ultimo anio de entrenamiento (default 2024)
--   holdout_year   anio evaluado (default 2025)
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off
\timing on

\if :{?model_version}
\else
  \set model_version 'risk-v1'
\endif
\if :{?train_from}
\else
  \set train_from 2013
\endif
\if :{?train_to}
\else
  \set train_to 2024
\endif
\if :{?holdout_year}
\else
  \set holdout_year 2025
\endif

BEGIN;

SET LOCAL work_mem = '512MB';

-- ----------------------------------------------------------------------------
-- 1. Predicciones y observado del holdout, solo con datos de entrenamiento
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS bt_scored;
CREATE TEMP TABLE bt_scored AS
WITH grid AS (
    SELECT DISTINCT grid_id
    FROM risk_cell_week
    WHERE era = 'SNPP'
),
n_cells AS (
    SELECT count(*)::float8 AS n FROM grid
),
cov AS (
    SELECT extract(week FROM acq_date)::smallint AS week_of_year,
           count(DISTINCT extract(isoyear FROM acq_date))::float8 AS n_train
    FROM coverage_daily
    WHERE has_data
      AND sensor = 'VIIRS_SNPP'
      AND extract(isoyear FROM acq_date) BETWEEN :train_from AND :train_to
    GROUP BY 1
),
cell AS (
    SELECT grid_id, week_of_year,
           count(*) FILTER (WHERE fire_days > 0)::float8 AS k_train
    FROM grid_weekly_sensor
    WHERE sensor = 'VIIRS_SNPP'
      AND isoyear BETWEEN :train_from AND :train_to
    GROUP BY 1, 2
),
fire AS (
    SELECT week_of_year,
           count(DISTINCT (grid_id, isoyear))
               FILTER (WHERE fire_days > 0)::float8 AS fire_cell_years
    FROM grid_weekly_sensor
    WHERE sensor = 'VIIRS_SNPP'
      AND isoyear BETWEEN :train_from AND :train_to
    GROUP BY 1
),
prior AS (
    SELECT c.week_of_year, c.n_train,
           coalesce(f.fire_cell_years, 0) / (nc.n * c.n_train) AS mu
    FROM cov c
    CROSS JOIN n_cells nc
    LEFT JOIN fire f USING (week_of_year)
    WHERE c.n_train > 0
),
obs AS (
    SELECT grid_id, week_of_year
    FROM grid_weekly_sensor
    WHERE sensor = 'VIIRS_SNPP'
      AND isoyear = :holdout_year
    GROUP BY 1, 2
    HAVING bool_or(fire_days > 0)
)
SELECT g.grid_id, p.week_of_year,
       ((coalesce(c.k_train, 0) + 8 * p.mu) / (p.n_train + 8))::float8 AS p_hat,
       p.mu::float8 AS mu,
       (o.grid_id IS NOT NULL)::int AS y
FROM grid g
CROSS JOIN prior p
LEFT JOIN cell c ON c.grid_id = g.grid_id AND c.week_of_year = p.week_of_year
LEFT JOIN obs o ON o.grid_id = g.grid_id AND o.week_of_year = p.week_of_year;

ANALYZE bt_scored;

-- ----------------------------------------------------------------------------
-- 2. Metricas y persistencia
-- ----------------------------------------------------------------------------
DELETE FROM risk_backtest
WHERE model_version = :'model_version'
  AND era = 'SNPP'
  AND holdout_year = :holdout_year;

INSERT INTO risk_backtest (
    model_version, era, holdout_year, brier, brier_baseline, bss, auc, n)
WITH agg AS (
    SELECT avg((p_hat - y)^2)::float8  AS brier,
           avg((mu - y)^2)::float8     AS brier_baseline,
           count(*)::float8            AS n,
           sum(y)::float8              AS n_pos
    FROM bt_scored
),
ranks AS (
    SELECT y, p_hat,
           (2.0 * rank() OVER (ORDER BY p_hat)
            + count(*) OVER (PARTITION BY p_hat) - 1) / 2.0 AS avg_rank
    FROM bt_scored
),
auc AS (
    SELECT (sum(avg_rank * y) - max(a.n_pos) * (max(a.n_pos) + 1) / 2.0)
           / nullif(max(a.n_pos) * (max(a.n) - max(a.n_pos)), 0) AS value
    FROM ranks, agg a
)
SELECT :'model_version', 'SNPP', :holdout_year,
       round(a.brier::numeric, 8)::real,
       round(a.brier_baseline::numeric, 8)::real,
       round((1 - a.brier / nullif(a.brier_baseline, 0))::numeric, 4)::real,
       round(au.value::numeric, 4)::real,
       a.n::bigint
FROM agg a, auc au;

COMMIT;

-- ----------------------------------------------------------------------------
-- 3. Reporte
-- ----------------------------------------------------------------------------
\echo ''
\echo '=== B1. Metricas persistidas ==='
SELECT model_version, era, holdout_year,
       round(brier::numeric, 6)          AS brier,
       round(brier_baseline::numeric, 6) AS brier_base,
       bss, auc, n
FROM risk_backtest
WHERE holdout_year = :holdout_year
ORDER BY model_version;

\echo ''
\echo '=== B2. Tasa observada y predicha global ==='
SELECT count(*)                                   AS celdas_semana,
       sum(y)                                     AS con_fuego,
       round(avg(y)::numeric, 4)                  AS tasa_observada,
       round(avg(p_hat)::numeric, 5)              AS p_media,
       round(avg(mu)::numeric, 5)                 AS mu_media
FROM bt_scored;

\echo ''
\echo '=== B3. Curva de fiabilidad por decil (menor a mayor p) ==='
SELECT decil,
       count(*)                            AS celdas_semana,
       round(avg(p_hat)::numeric, 4)       AS p_media,
       round(avg(y)::numeric, 4)           AS observado,
       round((avg(p_hat) - avg(y))::numeric, 4) AS desvio
FROM (
    SELECT y, p_hat, ntile(10) OVER (ORDER BY p_hat) AS decil
    FROM bt_scored
) d
GROUP BY 1
ORDER BY 1;

\echo ''
\echo '=== B4. Desvio absoluto del decil superior (puerta de calidad: <= 0,10) ==='
WITH deciles AS (
    SELECT y, p_hat, ntile(10) OVER (ORDER BY p_hat) AS decil
    FROM bt_scored
), top_decile AS (
    SELECT avg(p_hat) AS p_media, avg(y) AS observado
    FROM deciles
    WHERE decil = 10
)
SELECT round(p_media::numeric, 4)                    AS p_media,
       round(observado::numeric, 4)                  AS observado,
       round(abs(p_media - observado)::numeric, 4)   AS desvio_abs
FROM top_decile;

DROP TABLE IF EXISTS bt_scored;
