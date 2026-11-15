package com.chronosfire.backend.controller;

import com.chronosfire.backend.dto.AlertResponse;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection;
import com.chronosfire.backend.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Alertas")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/alerts")
    @Operation(summary = "Alertas NRT agrupadas por celda")
    public AlertResponse alerts(
            @RequestParam(required = false) String bbox,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "1000") int limit) {
        return alertService.getAlerts(bbox, days, limit);
    }

    @GetMapping("/alerts/geojson")
    @Operation(summary = "Alertas NRT como FeatureCollection")
    public GeoJsonFeatureCollection geojson(
            @RequestParam(required = false) String bbox,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "1000") int limit) {
        return alertService.getGeoJson(bbox, days, limit);
    }
}
