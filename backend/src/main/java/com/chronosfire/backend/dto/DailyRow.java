package com.chronosfire.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyRow(
        LocalDate date,
        String gridId,
        double cellLat,
        double cellLon,
        boolean fireDay,
        int nDetections,
        List<String> sensors,
        Double frpMaxMw,
        Double frpDensityMaxWm2,
        String source,
        String dataStatus
) {
}
