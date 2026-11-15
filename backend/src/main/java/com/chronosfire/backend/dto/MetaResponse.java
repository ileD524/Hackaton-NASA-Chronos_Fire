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
        ProvisionalWindow provisional
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
}
