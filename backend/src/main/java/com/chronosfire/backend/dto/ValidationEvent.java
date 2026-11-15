package com.chronosfire.backend.dto;

public record ValidationEvent(
        String aoiId,
        String name,
        int isoyear,
        int week,
        int observed,
        Double baseline,
        int nRefYears,
        Double anomaly,
        String confidence,
        String verdict
) {
}
