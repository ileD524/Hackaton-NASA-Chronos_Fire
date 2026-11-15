package com.chronosfire.backend.dto;

import java.time.Instant;
import java.util.List;

public record Alert(
        double cellLat,
        double cellLon,
        Instant lastDetection,
        int nDetections,
        Double frpMaxMw,
        Double frpDensityMaxWm2,
        List<String> sensors,
        String confidenceMax,
        Double aoiAnomaly,
        String level,
        boolean industrialMasked
) {
}
