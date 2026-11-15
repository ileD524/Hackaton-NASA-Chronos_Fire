package com.chronosfire.backend.dto;

import java.util.List;
import java.util.Map;

public record GeoJsonFeatureCollection(String type, List<GeoJsonFeature> features) {

    public static GeoJsonFeatureCollection of(List<GeoJsonFeature> features) {
        return new GeoJsonFeatureCollection("FeatureCollection", features);
    }

    public record GeoJsonFeature(
            String type,
            GeoJsonPoint geometry,
            Map<String, Object> properties
    ) {

        public static GeoJsonFeature point(double longitude, double latitude, Map<String, Object> properties) {
            return new GeoJsonFeature("Feature", new GeoJsonPoint("Point", List.of(longitude, latitude)), properties);
        }
    }

    public record GeoJsonPoint(String type, List<Double> coordinates) {
    }
}
