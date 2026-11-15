package com.chronosfire.backend.dto;

import java.util.Map;

public record MonthlyCell(
        int year,
        int month,
        int fireCells,
        int fireDays,
        int nDetections,
        Map<String, Integer> detectionsBySensor,
        int hotspots,
        Map<String, Integer> hotspotsBySensor,
        boolean partial,
        String dataStatus,
        Double baselineFireCells,
        Double anomaly,
        int referenceYears,
        String confidence
) {
}
