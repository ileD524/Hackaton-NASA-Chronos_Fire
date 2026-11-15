package com.chronosfire.backend.dto;

import java.util.Map;

public record WeeklyResponse(
        Bbox aoi,
        int year,
        int week,
        int observedFireCells,
        Double baselineFireCells,
        Double anomaly,
        int referenceYears,
        String referenceWindow,
        String observedSensor,
        String baselineSensor,
        int nCellsAoi,
        String confidence,
        boolean provisional,
        String dataStatus,
        Integer nDataYears,
        Map<String, Integer> fireCellsBySensor,
        Map<String, Integer> detectionsBySensor,
        Map<String, Double> frpBySensor,
        Double fireDayRateGivenDetection
) {
}
