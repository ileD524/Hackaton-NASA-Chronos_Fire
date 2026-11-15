package com.chronosfire.backend.dto;

public record HeatmapCell(
        String gridId,
        double cellLat,
        double cellLon,
        int fireCells,
        int fireDays,
        int nDetections,
        Double anomaly,
        String dataStatus
) {
}
