package com.chronosfire.backend.dto;

import java.util.Map;

public record HeatmapCell(
        String gridId,
        double cellLat,
        double cellLon,
        int fireCells,
        int fireDays,
        int nDetections,
        int hotspots,
        Map<String, Integer> hotspotsBySensor,
        Double anomaly,
        String dataStatus,
        Double fireProbability,
        Double probabilityLow,
        Double probabilityHigh,
        Integer probabilityRefYears,
        String probabilityReliability
) {
}
