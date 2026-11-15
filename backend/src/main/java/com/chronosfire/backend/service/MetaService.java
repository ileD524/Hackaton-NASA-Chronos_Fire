package com.chronosfire.backend.service;

import com.chronosfire.backend.dto.MetaResponse;
import com.chronosfire.backend.repository.MetaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class MetaService {

    private static final Logger log = LoggerFactory.getLogger(MetaService.class);

    private final MetaRepository repository;

    public MetaService(MetaRepository repository) {
        this.repository = repository;
    }

    @Cacheable("meta")
    public MetaResponse getMeta() {
        long start = System.nanoTime();

        List<MetaResponse.SensorCoverage> sensors = repository.sensorCoverage();
        long t = mark("sensorCoverage", start);

        Instant lastDetectionAt = repository.lastDetectionAt();
        t = mark("lastDetectionAt", t);

        MetaResponse.DateRange dateRange = repository.dateRange();
        t = mark("dateRange", t);

        MetaResponse.Counts counts = repository.counts();
        mark("counts", t);

        // Se deriva de sensorCoverage: evita un escaneo extra de 7 M de filas.
        List<String> sources = sensors.stream()
                .map(MetaResponse.SensorCoverage::source)
                .distinct()
                .sorted()
                .toList();

        LocalDate nrtFrom = sensors.stream()
                .filter(sensor -> "NRT".equals(sensor.source()))
                .map(MetaResponse.SensorCoverage::from)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);

        LocalDate nrtTo = sensors.stream()
                .filter(sensor -> "NRT".equals(sensor.source()))
                .map(MetaResponse.SensorCoverage::to)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);

        MetaResponse.ProvisionalWindow provisional = nrtFrom == null
                ? null
                : new MetaResponse.ProvisionalWindow(nrtFrom, nrtTo, "NRT");

        return new MetaResponse(
                "v1",
                Instant.now(),
                lastDetectionAt,
                "chronos_fire",
                0.05,
                sensors,
                sources,
                dateRange,
                counts,
                metricDefinitions(),
                limitations(),
                "NASA FIRMS: MODIS C6.1, VIIRS SNPP, VIIRS NOAA-20 y VIIRS NOAA-21.",
                provisional);
    }

    private long mark(String name, long start) {
        long now = System.nanoTime();
        log.debug("meta.{}={}ms", name, (now - start) / 1_000_000L);
        return now;
    }

    private Map<String, String> metricDefinitions() {
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put("fire_day",
                "TRUE si una celda de 0,05 grados tuvo al menos una deteccion de vegetacion con confianza nominal/alta en el dia UTC.");
        definitions.put("fire_cells",
                "Cantidad de celdas de 0,05 grados con fire_day en el periodo consultado.");
        definitions.put("n_detections",
                "Cantidad de detecciones FIRMS. No es cantidad de incendios y no debe sumarse entre sensores.");
        definitions.put("anomaly",
                "observedFireCells / baselineFireCells. El baseline es la mediana multianual por AOI, con leave-one-out.");
        definitions.put("baseline_fire_cells",
                "Mediana de celdas con actividad en los anios de referencia de la era del sensor.");
        definitions.put("confidence",
                "high si nRefYears >= 10 y baseline >= 10; low si cumple los minimos; insufficient si no hay anomalia valida.");
        definitions.put("data_status",
                "observed, no_data o unknown. no_data indica que coverage_daily no registra cobertura para ese periodo; unknown se usa cuando no hay informacion de cobertura.");
        definitions.put("provisional",
                "TRUE para 2026 o para periodos cubiertos por NRT.");
        return definitions;
    }

    private List<String> limitations() {
        return List.of(
                "La grilla es de 0,05 grados (~5 km); no representa area quemada ni perimetros.",
                "La cobertura es un proxy por dias con detecciones en coverage_daily; no descuenta nubosidad ni overpass.",
                "Una semana sin filas no confirma ausencia de fuego.",
                "No hay calibracion fisica de eficiencia de deteccion ni de FRP.",
                "2026 es NRT provisional y no se usa como baseline.",
                "MODIS y VIIRS no deben sumarse como si fueran el mismo sensor.");
    }
}
