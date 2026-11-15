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

    private static final double BSS_THRESHOLD = 0.05;
    private static final String PRIMARY_BACKTEST = "risk-v1";

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
        t = mark("counts", t);

        String riskModelVersion = repository.riskModelVersion();
        t = mark("riskModelVersion", t);

        List<MetaResponse.RiskBacktest> backtests = repository.riskBacktest();
        mark("riskBacktest", t);

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
                provisional,
                riskMethodology(riskModelVersion, backtests));
    }

    /**
     * Publica la calibracion del modelo de riesgo (fase 4). Si el backtest no
     * corrio (o el dump no trae las tablas), devuelve {@code null}.
     */
    private MetaResponse.RiskMethodology riskMethodology(
            String riskModelVersion,
            List<MetaResponse.RiskBacktest> backtests) {
        if (backtests.isEmpty()) {
            return null;
        }
        MetaResponse.RiskBacktest primary = backtests.stream()
                .filter(row -> PRIMARY_BACKTEST.equals(row.modelVersion()))
                .findFirst()
                .orElse(backtests.get(0));
        Double stabilityBss = backtests.stream()
                .filter(row -> row.modelVersion() != null
                        && row.modelVersion().startsWith(PRIMARY_BACKTEST + "-t"))
                .map(MetaResponse.RiskBacktest::bss)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        double bss = primary.bss() == null ? 0.0 : primary.bss();
        String status = bss >= BSS_THRESHOLD ? "high" : "low";
        String publishedAs = riskModelVersion == null
                ? ("low".equals(status) ? "risk-v1-low" : "risk-v1")
                : riskModelVersion;
        String note = "low".equals(status)
                ? String.format(java.util.Locale.ROOT,
                        "BSS %.4f < %.2f en el holdout %d: se publica como %s. "
                                + "Es frecuencia historica, no prediccion ni riesgo oficial.",
                        bss, BSS_THRESHOLD, primary.holdoutYear(), publishedAs)
                : String.format(java.util.Locale.ROOT,
                        "BSS %.4f >= %.2f en el holdout %d. "
                                + "Sigue siendo frecuencia historica, no prediccion ni riesgo oficial.",
                        bss, BSS_THRESHOLD, primary.holdoutYear());

        return new MetaResponse.RiskMethodology(
                publishedAs,
                status,
                primary.holdoutYear(),
                round(primary.brier(), 5),
                round(primary.bss(), 4),
                round(primary.auc(), 3),
                primary.cellsWeek(),
                round(stabilityBss, 4),
                BSS_THRESHOLD,
                note);
    }

    private Double round(Double value, int decimals) {
        if (value == null) {
            return null;
        }
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
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
        definitions.put("fire_probability",
                "Frecuencia historica estimada de actividad por celda 0,05 y semana ISO: p = (k + alpha) / (n + alpha + beta). No es prediccion ni riesgo oficial.");
        definitions.put("probability_reliability",
                "high con n >= 10; low con 5 <= n < 10; insufficient con n < 5. Sin n no se muestra p.");
        definitions.put("activity_percentile",
                "Fraccion de anios de referencia con n_detections semanal menor o igual al observado. Solo se calcula con days = 7.");
        return definitions;
    }

    private List<String> limitations() {
        return List.of(
                "La grilla es de 0,05 grados (~5 km); no representa area quemada ni perimetros.",
                "La cobertura es un proxy por dias con detecciones en coverage_daily; no descuenta nubosidad ni overpass.",
                "Una semana sin filas no confirma ausencia de fuego.",
                "No hay calibracion fisica de eficiencia de deteccion ni de FRP.",
                "2026 es NRT provisional y no se usa como baseline.",
                "MODIS y VIIRS no deben sumarse como si fueran el mismo sensor.",
                "La capa de riesgo se publica como risk-v1-low: su BSS en el holdout 2025 queda debajo de 0,05.");
    }
}
