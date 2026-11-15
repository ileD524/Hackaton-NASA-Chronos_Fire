package com.chronosfire.backend.dto;

public record Bbox(
        double minLon,
        double minLat,
        double maxLon,
        double maxLat
) {
}
