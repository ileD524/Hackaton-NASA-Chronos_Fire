package com.chronosfire.backend.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Smoke test del contrato estatico. No valida la implementacion: verifica que
 * el YAML documente los endpoints y campos que la API expone.
 */
class OpenApiContractTest {

    @Test
    void documentsTheImplementedContract() throws Exception {
        Path specPath = Path.of("openapi", "chronosfire-api.yaml");
        assertTrue(Files.exists(specPath), "Falta openapi/chronosfire-api.yaml");

        Map<String, Object> root = new Yaml().load(Files.readString(specPath));
        Map<String, Object> paths = map(root.get("paths"));
        assertEquals(15, paths.size());
        assertTrue(paths.containsKey("/api/calendar/monthly"));
        assertTrue(paths.containsKey("/api/calendar/heatmap"));
        assertTrue(paths.containsKey("/api/detections/export.csv"));
        assertTrue(paths.containsKey("/api/calendar/export.csv"));

        assertTrue(parameters(root, "/api/calendar/monthly").stream()
                .anyMatch(parameter -> String.valueOf(parameter.get("$ref"))
                        .endsWith("/MonthlySensor")));

        Map<String, Object> monthly = at(root,
                "components", "schemas", "MonthlyCell", "properties");
        assertTrue(monthly.containsKey("hotspots"));
        assertTrue(monthly.containsKey("hotspotsBySensor"));
        assertTrue(monthly.containsKey("baselineFireCells"));
        assertTrue(monthly.containsKey("anomaly"));
        assertTrue(monthly.containsKey("referenceYears"));
        assertTrue(monthly.containsKey("confidence"));

        Map<String, Object> daily = at(root,
                "components", "schemas", "DailyRow", "properties");
        assertTrue(daily.containsKey("hotspots"));
        assertTrue(daily.containsKey("hotspotsBySensor"));

        Map<String, Object> heatmap = at(root,
                "components", "schemas", "HeatmapCell", "properties");
        assertTrue(heatmap.containsKey("hotspots"));
        assertTrue(heatmap.containsKey("hotspotsBySensor"));
        assertTrue(heatmap.containsKey("fireProbability"));
        assertTrue(heatmap.containsKey("probabilityLow"));
        assertTrue(heatmap.containsKey("probabilityHigh"));
        assertTrue(heatmap.containsKey("probabilityRefYears"));
        assertTrue(heatmap.containsKey("probabilityReliability"));

        Map<String, Object> alert = at(root,
                "components", "schemas", "Alert", "properties");
        assertTrue(alert.containsKey("fireProbability"));
        assertTrue(alert.containsKey("probabilityLow"));
        assertTrue(alert.containsKey("probabilityHigh"));
        assertTrue(alert.containsKey("probabilityRefYears"));
        assertTrue(alert.containsKey("probabilityReliability"));
        assertTrue(alert.containsKey("activityPercentile"));

        Map<String, Object> detectionsHeaders = at(root,
                "paths", "/api/detections/export.csv", "get", "responses", "200", "headers");
        assertTrue(detectionsHeaders.containsKey("X-Row-Limit"));
        assertTrue(detectionsHeaders.containsKey("X-Returned-Rows"));
        assertTrue(detectionsHeaders.containsKey("X-Truncated"));

        Map<String, Object> calendarHeaders = at(root,
                "paths", "/api/calendar/export.csv", "get", "responses", "200", "headers");
        assertTrue(calendarHeaders.containsKey("X-Row-Limit"));
        assertTrue(calendarHeaders.containsKey("X-Returned-Rows"));
        assertTrue(calendarHeaders.containsKey("X-Truncated"));

        Map<String, Object> monthlySensor = at(root,
                "components", "parameters", "MonthlySensor");
        assertNotNull(monthlySensor.get("description"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parameters(Map<String, Object> root, String path) {
        Object value = get(root, "paths", path, "get", "parameters");
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> at(Map<String, Object> root, String... keys) {
        return map(get(root, keys));
    }

    private static Object get(Map<String, Object> root, String... keys) {
        Object current = root;
        for (String key : keys) {
            assertTrue(current instanceof Map, "Se esperaba un mapa antes de " + key);
            current = ((Map<?, ?>) current).get(key);
            assertNotNull(current, "Falta la clave " + key);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
