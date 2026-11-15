package com.chronosfire.backend.service;

import com.chronosfire.backend.dto.Alert;
import com.chronosfire.backend.dto.AlertResponse;
import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection.GeoJsonFeature;
import com.chronosfire.backend.dto.WeeklyResponse;
import com.chronosfire.backend.repository.AlertRepository;
import com.chronosfire.backend.repository.AlertRepository.AlertRow;
import com.chronosfire.backend.service.RiskService.Probability;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private static final Bbox DEFAULT_AOI = new Bbox(-73.6, -55.1, -53.6, -21.8);

    private final AlertRepository repository;
    private final CalendarService calendarService;
    private final RiskService riskService;

    public AlertService(
            AlertRepository repository,
            CalendarService calendarService,
            RiskService riskService) {
        this.repository = repository;
        this.calendarService = calendarService;
        this.riskService = riskService;
    }

    @Cacheable(value = "alerts", key = "#bboxParam + '|' + #days + '|' + #limit")
    public AlertResponse getAlerts(String bboxParam, int days, int limit) {
        int safeDays = validateDays(days);
        int safeLimit = validateLimit(limit);
        Bbox bbox = parseBbox(bboxParam);
        Instant now = Instant.now();
        Instant since = now.minus(safeDays, ChronoUnit.DAYS);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int isoWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int isoYear = today.get(IsoFields.WEEK_BASED_YEAR);
        String riskEra = isoYear >= 2013 ? "SNPP" : "MODIS";

        WeeklyResponse weekly = calendarService.getWeekly(
                bboxString(bbox), isoYear, isoWeek, "AUTO");
        Double aoiAnomaly = weekly.anomaly();

        List<AlertRow> rows = repository.alerts(bbox, since, safeLimit);

        Map<String, Integer> observedByCell = new LinkedHashMap<>();
        for (AlertRow row : rows) {
            observedByCell.put(row.gridId(), row.nDetections());
        }
        Map<String, Probability> probabilities = riskService.alertProbabilities(
                observedByCell, isoWeek, riskEra, safeDays);

        List<Alert> alerts = new ArrayList<>(rows.size());
        for (AlertRow row : rows) {
            Probability probability = probabilities.getOrDefault(row.gridId(), Probability.unknown());
            alerts.add(new Alert(
                    row.cellLat(),
                    row.cellLon(),
                    row.lastDetection(),
                    row.nDetections(),
                    row.frpMaxMw(),
                    row.frpDensityMaxWm2(),
                    row.sensors(),
                    confidenceClass(row.confidenceMax()),
                    aoiAnomaly,
                    RiskMath.level(
                            probability.fireProbability(),
                            probability.activityPercentile(),
                            row.nDetections()),
                    false,
                    probability.fireProbability(),
                    probability.probabilityLow(),
                    probability.probabilityHigh(),
                    probability.probabilityRefYears(),
                    probability.probabilityReliability(),
                    probability.activityPercentile()));
        }

        return new AlertResponse(
                now,
                now,
                repository.latestNrt(),
                safeDays,
                bbox,
                alerts.size(),
                true,
                alerts);
    }

    @Cacheable(value = "alerts", key = "'geo|' + #bboxParam + '|' + #days + '|' + #limit")
    public GeoJsonFeatureCollection getGeoJson(String bboxParam, int days, int limit) {
        AlertResponse response = getAlerts(bboxParam, days, limit);
        List<GeoJsonFeature> features = new ArrayList<>(response.alerts().size());
        for (Alert alert : response.alerts()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("cellLat", alert.cellLat());
            properties.put("cellLon", alert.cellLon());
            properties.put("lastDetection", alert.lastDetection() == null ? null : alert.lastDetection().toString());
            properties.put("nDetections", alert.nDetections());
            properties.put("frpMaxMw", alert.frpMaxMw());
            properties.put("frpDensityMaxWm2", alert.frpDensityMaxWm2());
            properties.put("sensors", alert.sensors());
            properties.put("confidenceMax", alert.confidenceMax());
            properties.put("aoiAnomaly", alert.aoiAnomaly());
            properties.put("level", alert.level());
            properties.put("industrialMasked", alert.industrialMasked());
            properties.put("fireProbability", alert.fireProbability());
            properties.put("probabilityLow", alert.probabilityLow());
            properties.put("probabilityHigh", alert.probabilityHigh());
            properties.put("probabilityRefYears", alert.probabilityRefYears());
            properties.put("probabilityReliability", alert.probabilityReliability());
            properties.put("activityPercentile", alert.activityPercentile());
            features.add(GeoJsonFeature.point(alert.cellLon(), alert.cellLat(), properties));
        }
        return GeoJsonFeatureCollection.of(features);
    }

    private String confidenceClass(int confidenceNorm) {
        if (confidenceNorm >= 80) {
            return "high";
        }
        if (confidenceNorm >= 50) {
            return "nominal";
        }
        return "low";
    }

    private int validateDays(int days) {
        if (days < 1 || days > 30) {
            throw new IllegalArgumentException("days debe estar entre 1 y 30.");
        }
        return days;
    }

    private int validateLimit(int limit) {
        if (limit <= 0) {
            return 1000;
        }
        if (limit > 5000) {
            throw new IllegalArgumentException("limit maximo: 5000.");
        }
        return limit;
    }

    private Bbox parseBbox(String bboxParam) {
        if (bboxParam == null || bboxParam.isBlank()) {
            return DEFAULT_AOI;
        }
        String[] parts = bboxParam.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("bbox debe tener cuatro valores: oeste,sur,este,norte.");
        }
        try {
            double minLon = Double.parseDouble(parts[0].trim());
            double minLat = Double.parseDouble(parts[1].trim());
            double maxLon = Double.parseDouble(parts[2].trim());
            double maxLat = Double.parseDouble(parts[3].trim());
            if (!(minLon < maxLon) || !(minLat < maxLat)) {
                throw new IllegalArgumentException("bbox invalido: se espera oeste < este y sur < norte.");
            }
            if (minLon < -180 || maxLon > 180 || minLat < -90 || maxLat > 90) {
                throw new IllegalArgumentException("bbox fuera de rango.");
            }
            return new Bbox(minLon, minLat, maxLon, maxLat);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("bbox debe contener numeros validos.");
        }
    }

    private String bboxString(Bbox bbox) {
        return String.format(Locale.ROOT, "%s,%s,%s,%s",
                bbox.minLon(), bbox.minLat(), bbox.maxLon(), bbox.maxLat());
    }
}
