package com.chronosfire.backend.dto;

public record AoiWindow(
        int isoyear,
        int weekFrom,
        int weekTo,
        Integer expectedPeak
) {
}
