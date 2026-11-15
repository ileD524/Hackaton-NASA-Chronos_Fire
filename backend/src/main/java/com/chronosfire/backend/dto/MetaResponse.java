package com.chronosfire.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record MetaResponse(
        String version,
        Instant generatedAt,
        Instant lastDetectionAt,
        String database,
        double gridResolutionDeg,
        List<SensorCoverage> sensors,
        List<String> sources,
        DateRange dateRange,
        Counts counts,
        Map<String, String> metricDefinitions,
        List<String> limitations,
        String attribution,
        ProvisionalWindow provisional,
        RiskMethodology risk
) {

    public record SensorCoverage(
            String sensor,
            String source,
            long rows,
            LocalDate from,
            LocalDate to
    ) {
    }

    public record DateRange(LocalDate from, LocalDate to) {
    }

    public record Counts(
            long detections,
            long gridDaily,
            long gridWeekly,
            long gridWeeklySensor,
            long calendarWeekly,
            long industrialCells,
            long coverageDaily,
            long validationAois
    ) {
    }

    public record ProvisionalWindow(LocalDate from, LocalDate to, String reason) {
    }

    /**
     * Calibracion publicada de la capa de riesgo (fase 4). Sale de
     * {@code risk_backtest}; {@code null} si el backtest no se corrio.
     */
    public record RiskMethodology(
            String modelVersion,
            String calibrationStatus,
            Integer holdoutYear,
            Double brier,
            Double bss,
            Double auc,
            Long cellsWeek,
            Double stabilityBss,
            Double bssThreshold,
            String note
    ) {
    }

    public record RiskBacktest(
            String modelVersion,
            Integer holdoutYear,
            Double brier,
            Double bss,
            Double auc,
            Long cellsWeek
    ) {
    }
}
