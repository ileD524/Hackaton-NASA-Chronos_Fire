package com.chronosfire.backend.controller;

import com.chronosfire.backend.dto.DetectionPage;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection;
import com.chronosfire.backend.service.DetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Detecciones")
public class DetectionController {

    private final DetectionService detectionService;

    public DetectionController(DetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @GetMapping("/detections")
    @Operation(summary = "Detecciones paginadas sobre la vista canonica")
    public DetectionPage detections(
            @RequestParam(required = false) String bbox,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String sensor,
            @RequestParam(required = false) List<String> confidence,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean vegetation,
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return detectionService.getPage(
                bbox, from, to, sensor, confidence, type, source, vegetation, limit, offset);
    }

    @GetMapping("/detections/geojson")
    @Operation(summary = "Detecciones como FeatureCollection")
    public GeoJsonFeatureCollection geojson(
            @RequestParam(required = false) String bbox,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String sensor,
            @RequestParam(required = false) List<String> confidence,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean vegetation,
            @RequestParam(defaultValue = "1000") int limit) {
        return detectionService.getGeoJson(
                bbox, from, to, sensor, confidence, type, source, vegetation, limit);
    }

    @GetMapping("/detections/export.csv")
    @Operation(summary = "Exporta detecciones en CSV")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String bbox,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String sensor,
            @RequestParam(required = false) List<String> confidence,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean vegetation,
            @RequestParam(defaultValue = "50000") int limit) {
        String csv = detectionService.exportCsv(
                bbox, from, to, sensor, confidence, type, source, vegetation, limit);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("chronosfire-detecciones.csv")
                                .build()
                                .toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
