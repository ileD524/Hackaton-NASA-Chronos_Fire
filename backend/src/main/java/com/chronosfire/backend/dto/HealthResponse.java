package com.chronosfire.backend.dto;

import java.time.Instant;

public record HealthResponse(
        String status,
        boolean database,
        String databaseVersion,
        long latencyMs,
        Instant lastDetectionAt,
        long detectionsApprox
) {
}
