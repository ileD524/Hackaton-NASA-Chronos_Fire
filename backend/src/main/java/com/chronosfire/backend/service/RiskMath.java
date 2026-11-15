package com.chronosfire.backend.service;

import java.util.List;

/**
 * Aritmetica cerrada del riesgo estadistico (PLAN_RIESGO_ESTADISTICO.md, §3).
 *
 * <p>Sin dependencias externas: Wilson, media posterior Beta-Binomial,
 * confiabilidad y percentil de actividad. Todo se calcula en Java para no
 * agregar extensiones a PostgreSQL ni librerias de terceros.</p>
 */
public final class RiskMath {

    /** Cuantil normal estandar para el intervalo del 90 %. */
    public static final double Z90 = 1.6448536269514722;

    /** Fuerza del prior empirico por semana ISO. */
    public static final double KAPPA = 8.0;

    private RiskMath() {
    }

    /**
     * Media posterior Beta-Binomial: {@code (k + alpha) / (n + alpha + beta)}.
     *
     * @return {@code null} si no hay anios de referencia ({@code n <= 0})
     */
    public static Double posterior(int k, int n, double alpha, double beta) {
        if (n <= 0) {
            return null;
        }
        double denominator = n + alpha + beta;
        if (denominator <= 0) {
            return null;
        }
        return round(clamp((k + alpha) / denominator));
    }

    /**
     * Intervalo de Wilson al 90 % sobre la proporcion cruda {@code k/n}.
     *
     * @return {@code {limiteInferior, limiteSuperior}} o {@code null} si
     *         {@code n <= 0}
     */
    public static double[] wilson(int k, int n) {
        if (n <= 0) {
            return null;
        }
        double phat = k / (double) n;
        double z2 = Z90 * Z90;
        double denominator = 1 + z2 / n;
        double center = (phat + z2 / (2.0 * n)) / denominator;
        double half = Z90 * Math.sqrt(phat * (1 - phat) / n + z2 / (4.0 * n * n)) / denominator;
        return new double[]{
                round(clamp(center - half)),
                round(clamp(center + half))
        };
    }

    /**
     * Confiabilidad del producto segun los anios de referencia (§3.6).
     */
    public static String reliability(int refYears) {
        if (refYears >= 10) {
            return "high";
        }
        if (refYears >= 5) {
            return "low";
        }
        return "insufficient";
    }

    /**
     * Percentil de actividad: fraccion de anios de referencia con
     * {@code n_detections} semanal menor o igual al observado.
     *
     * <p>{@code historical} solo trae los anios con actividad (una fila por
     * anio en {@code grid_weekly_sensor}); los anios sin fila tuvieron 0
     * detecciones y cuentan como valores por debajo del observado. Por eso el
     * denominador es {@code refYears}, no {@code historical.size()}.</p>
     *
     * @return {@code null} si no se puede calcular
     */
    public static Double activityPercentile(List<Integer> historical, int refYears, Integer observed) {
        if (observed == null || refYears <= 0) {
            return null;
        }
        long atOrBelow = 0;
        for (Integer value : historical) {
            if (value != null && value <= observed) {
                atOrBelow++;
            }
        }
        long missingYears = Math.max(0, refYears - historical.size());
        return round((atOrBelow + missingYears) / (double) refYears);
    }

    /**
     * Nivel de la alerta (enum {@code info | watch | warning}).
     *
     * <p>Se conserva el enum del contrato; los cortes son los propuestos en
     * la fase 2 y deben reemplazarse por los cuantiles observados despues de
     * la fase 4.</p>
     */
    public static String level(Double fireProbability, Double activityPercentile) {
        if (activityPercentile != null && activityPercentile >= 0.90) {
            return "warning";
        }
        if (fireProbability != null && fireProbability >= 0.50) {
            return "warning";
        }
        if (activityPercentile != null && activityPercentile >= 0.75) {
            return "watch";
        }
        if (fireProbability != null && fireProbability >= 0.25) {
            return "watch";
        }
        return "info";
    }

    /** Trunca a 4 decimales, el mismo redondeo que usa {@code risk_cell_week}. */
    public static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static double clamp(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }
}
