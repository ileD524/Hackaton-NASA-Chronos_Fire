package com.chronosfire.backend.dto;

public record TrendRow(
        int year,
        int fireCells,
        Double baselineFireCells,
        Double anomaly,
        int referenceYears,
        String confidence,
        boolean provisional,
        String observedSensor,
        String baselineSensor
) {
}
