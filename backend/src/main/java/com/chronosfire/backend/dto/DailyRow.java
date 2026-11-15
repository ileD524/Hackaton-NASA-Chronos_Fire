package com.chronosfire.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DailyRow(
        LocalDate date,
        String gridId,
        double cellLat,
        double cellLon,
        boolean fireDay,
        int nDetections,
        List<String> sensors,
        int hotspots,
        Map<String, Integer> hotspotsBySensor,
        Double frpMaxMw,
        Double frpDensityMaxWm2,
        String source,
        String dataStatus
) {
}
