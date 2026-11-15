package com.chronosfire.backend.dto;

import java.time.Instant;
import java.util.List;

public record AlertResponse(
        Instant generatedAt,
        Instant asOf,
        Instant latestAvailable,
        int days,
        Bbox aoi,
        int count,
        boolean provisional,
        List<Alert> alerts
) {
}
