package com.chronosfire.backend.service;

import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.Detection;
import com.chronosfire.backend.dto.DetectionPage;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection.GeoJsonFeature;
import com.chronosfire.backend.repository.DetectionFilter;
import com.chronosfire.backend.repository.DetectionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DetectionService {

    private static final Bbox DEFAULT_AOI = new Bbox(-73.6, -55.1, -53.6, -21.8);
    private static final Set<String> SENSORS = Set.of(
            "ALL", "MODIS", "VIIRS_SNPP", "VIIRS_NOAA20", "VIIRS_NOAA21");
    private static final Set<String> CONFIDENCE = Set.of("low", "nominal", "high");
    private static final Set<String> SOURCES = Set.of("SP", "NRT");

    private final DetectionRepository repository;

    public DetectionService(DetectionRepository repository) {
        this.repository = repository;
    }

    public DetectionPage getPage(
            String bboxParam,
            LocalDate from,
            LocalDate to,
            String sensorParam,
            List<String> confidenceParam,
            Integer fireType,
            String sourceParam,
            Boolean vegetation,
            int limit,
            int offset) {
        DetectionFilter filter = filter(bboxParam, from, to, sensorParam, confidenceParam, fireType, sourceParam, vegetation);
        int safeLimit = validateLimit(limit, 1000, 50000);
        if (offset < 0) {
            throw new IllegalArgumentException("offset no puede ser negativo.");
        }
        return repository.find(filter, safeLimit, offset);
    }

    public GeoJsonFeatureCollection getGeoJson(
            String bboxParam,
            LocalDate from,
            LocalDate to,
            String sensorParam,
            List<String> confidenceParam,
            Integer fireType,
            String sourceParam,
            Boolean vegetation,
            int limit) {
        DetectionFilter filter = filter(bboxParam, from, to, sensorParam, confidenceParam, fireType, sourceParam, vegetation);
        int safeLimit = validateLimit(limit, 1000, 50000);
        List<Detection> detections = repository.findForExport(filter, safeLimit);
        List<GeoJsonFeature> features = new ArrayList<>(detections.size());
        for (Detection detection : detections) {
            features.add(GeoJsonFeature.point(
                    detection.longitude(),
                    detection.latitude(),
                    properties(detection)));
        }
        return GeoJsonFeatureCollection.of(features);
    }

    public String exportCsv(
            String bboxParam,
            LocalDate from,
            LocalDate to,
            String sensorParam,
            List<String> confidenceParam,
            Integer fireType,
            String sourceParam,
            Boolean vegetation,
            int limit) {
        DetectionFilter filter = filter(bboxParam, from, to, sensorParam, confidenceParam, fireType, sourceParam, vegetation);
        int safeLimit = validateLimit(limit, 50000, 50000);
        List<Detection> detections = repository.findForExport(filter, safeLimit);

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("sensor,source,acquired_at,acq_date,latitude,longitude,")
                .append("confidence_raw,confidence_norm,confidence_class,fire_type,is_vegetation,")
                .append("industrial_mask,frp_mw,frp_density_wm2,pixel_area_km2,quality_flags,product_version\n");
        for (Detection d : detections) {
            csv.append(csv(d.sensor())).append(',')
                    .append(csv(d.source())).append(',')
                    .append(csv(d.acquiredAt() == null ? null : d.acquiredAt().toString())).append(',')
                    .append(csv(d.acqDate() == null ? null : d.acqDate().toString())).append(',')
                    .append(d.latitude()).append(',')
                    .append(d.longitude()).append(',')
                    .append(csv(d.confidenceRaw())).append(',')
                    .append(d.confidenceNorm() == null ? "" : d.confidenceNorm()).append(',')
                    .append(csv(d.confidenceClass())).append(',')
                    .append(d.fireType() == null ? "" : d.fireType()).append(',')
                    .append(d.isVegetation() == null ? "" : d.isVegetation()).append(',')
                    .append(d.industrialMask()).append(',')
                    .append(d.frpMw() == null ? "" : d.frpMw()).append(',')
                    .append(d.frpDensityWm2() == null ? "" : d.frpDensityWm2()).append(',')
                    .append(d.pixelAreaKm2() == null ? "" : d.pixelAreaKm2()).append(',')
                    .append(csv(d.qualityFlags())).append(',')
                    .append(csv(d.productVersion())).append('\n');
        }
        return csv.toString();
    }

    private DetectionFilter filter(
            String bboxParam,
            LocalDate from,
            LocalDate to,
            String sensorParam,
            List<String> confidenceParam,
            Integer fireType,
            String sourceParam,
            Boolean vegetation) {
        validateRange(from, to);
        if (fireType != null && (fireType < 0 || fireType > 3)) {
            throw new IllegalArgumentException("type debe estar entre 0 y 3.");
        }
        String source = sourceParam == null || sourceParam.isBlank()
                ? null
                : sourceParam.trim().toUpperCase(Locale.ROOT);
        if (source != null && !SOURCES.contains(source)) {
            throw new IllegalArgumentException("source debe ser SP o NRT.");
        }
        return new DetectionFilter(
                parseBbox(bboxParam),
                from,
                to,
                normalizeSensor(sensorParam),
                normalizeConfidence(confidenceParam),
                fireType,
                source,
                vegetation);
    }

    private Map<String, Object> properties(Detection d) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("sensor", d.sensor());
        properties.put("source", d.source());
        properties.put("acquiredAt", d.acquiredAt() == null ? null : d.acquiredAt().toString());
        properties.put("acqDate", d.acqDate() == null ? null : d.acqDate().toString());
        properties.put("confidenceClass", d.confidenceClass());
        properties.put("confidenceNorm", d.confidenceNorm());
        properties.put("fireType", d.fireType());
        properties.put("isVegetation", d.isVegetation());
        properties.put("industrialMask", d.industrialMask());
        properties.put("frpMw", d.frpMw());
        properties.put("frpDensityWm2", d.frpDensityWm2());
        properties.put("productVersion", d.productVersion());
        return properties;
    }

    private String normalizeSensor(String sensorParam) {
        String value = sensorParam == null || sensorParam.isBlank()
                ? "ALL"
                : sensorParam.trim().toUpperCase(Locale.ROOT);
        if ("AUTO".equals(value)) {
            return "ALL";
        }
        if (!SENSORS.contains(value)) {
            throw new IllegalArgumentException(
                    "Sensor invalido: " + sensorParam
                            + ". Use ALL, AUTO, MODIS, VIIRS_SNPP, VIIRS_NOAA20 o VIIRS_NOAA21.");
        }
        return value;
    }

    private List<String> normalizeConfidence(List<String> confidenceParam) {
        if (confidenceParam == null || confidenceParam.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String raw : confidenceParam) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String part : raw.split(",")) {
                String value = part.trim().toLowerCase(Locale.ROOT);
                if (!CONFIDENCE.contains(value)) {
                    throw new IllegalArgumentException("confidence debe ser low, nominal o high.");
                }
                if (!values.contains(value)) {
                    values.add(value);
                }
            }
        }
        return values;
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

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from y to son requeridos.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from debe ser anterior o igual a to.");
        }
        if (from.isBefore(LocalDate.of(2000, 1, 1)) || to.isAfter(LocalDate.of(2026, 12, 31))) {
            throw new IllegalArgumentException("El rango debe estar entre 2000-01-01 y 2026-12-31.");
        }
        if (to.toEpochDay() - from.toEpochDay() > 3660) {
            throw new IllegalArgumentException("El rango supera el maximo de 3660 dias.");
        }
    }

    private int validateLimit(int limit, int defaultValue, int max) {
        if (limit <= 0) {
            return defaultValue;
        }
        if (limit > max) {
            throw new IllegalArgumentException("limit maximo: " + max + ".");
        }
        return limit;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
