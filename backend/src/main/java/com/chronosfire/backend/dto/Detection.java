package com.chronosfire.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

public record Detection(
        String sensor,
        String source,
        Instant acquiredAt,
        LocalDate acqDate,
        double latitude,
        double longitude,
        String confidenceRaw,
        Integer confidenceNorm,
        String confidenceClass,
        Integer fireType,
        Boolean isVegetation,
        boolean industrialMask,
        Double frpMw,
        Double frpDensityWm2,
        Double pixelAreaKm2,
        String qualityFlags,
        String productVersion
) {
}
