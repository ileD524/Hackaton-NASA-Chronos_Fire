package com.chronosfire.backend.dto;

import java.util.List;

public record DetectionPage(
        List<Detection> items,
        long total,
        int limit,
        int offset
) {
}
