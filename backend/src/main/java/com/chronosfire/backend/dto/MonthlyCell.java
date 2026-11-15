package com.chronosfire.backend.dto;

import java.util.Map;

public record MonthlyCell(
        int year,
        int month,
        int fireCells,
        int fireDays,
        int nDetections,
        Map<String, Integer> detectionsBySensor,
        boolean partial,
        String dataStatus
) {
}
