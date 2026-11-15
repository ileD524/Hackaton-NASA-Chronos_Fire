package com.chronosfire.backend.dto;

import java.util.List;

public record Aoi(
        String aoiId,
        String name,
        double minLat,
        double minLon,
        double maxLat,
        double maxLon,
        String observedSensor,
        String baselineSensor,
        int refFrom,
        int refTo,
        String status,
        String notes,
        List<AoiWindow> windows
) {
}
