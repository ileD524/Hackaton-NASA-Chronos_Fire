package com.chronosfire.backend.repository;

import com.chronosfire.backend.dto.Bbox;
import java.time.LocalDate;
import java.util.List;

public record DetectionFilter(
        Bbox bbox,
        LocalDate from,
        LocalDate to,
        String sensor,
        List<String> confidence,
        Integer fireType,
        String source,
        Boolean vegetation
) {
}
