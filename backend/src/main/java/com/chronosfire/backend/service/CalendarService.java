package com.chronosfire.backend.service;

import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.DailyRow;
import com.chronosfire.backend.dto.HeatmapCell;
import com.chronosfire.backend.dto.MonthlyCell;
import com.chronosfire.backend.dto.TrendRow;
import com.chronosfire.backend.dto.WeeklyResponse;
import com.chronosfire.backend.repository.CalendarRepository;
import com.chronosfire.backend.repository.CalendarRepository.AnnualSensor;
import com.chronosfire.backend.repository.CalendarRepository.Baseline;
import com.chronosfire.backend.repository.CalendarRepository.DailyHotspot;
import com.chronosfire.backend.repository.CalendarRepository.HeatmapHotspot;
import com.chronosfire.backend.repository.CalendarRepository.MonthFire;
import com.chronosfire.backend.repository.CalendarRepository.MonthSensorDetections;
import com.chronosfire.backend.repository.CalendarRepository.MonthlyHotspot;
import com.chronosfire.backend.repository.CalendarRepository.SensorWeek;
import com.chronosfire.backend.repository.CalendarRepository.WeekFlags;
import com.chronosfire.backend.repository.CalendarRepository.WeeklyExportRow;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CalendarService {

    private static final Bbox FULL_EXTENT = new Bbox(-180, -90, 180, 90);
    private static final Bbox DEFAULT_AOI = new Bbox(-73.6, -55.1, -53.6, -21.8);

    private final CalendarRepository repository;

    public CalendarService(CalendarRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = "weekly", key = "#bboxParam + '|' + #year + '|' + #week + '|' + #sensorParam")
    public WeeklyResponse getWeekly(String bboxParam, int year, int week, String sensorParam) {
        validateYear(year);
        validateWeek(week);
        LocalDate weekStart = weekStart(year, week);
        LocalDate weekEnd = weekStart.plusDays(6);

        Bbox requested = parseBbox(bboxParam);
        Bbox responseBbox = requested == null ? DEFAULT_AOI : requested;
        Bbox queryBbox = requested == null ? FULL_EXTENT : requested;

        SensorPlan plan = resolvePlan(year, sensorParam);

        List<SensorWeek> rows = repository.bySensor(year, week, queryBbox);
        SensorWeek selected = rows.stream()
                .filter(row -> row.sensor().equals(plan.observedSensor()))
                .findFirst()
                .orElse(null);

        Baseline baseline = plan.anomalyAllowed()
                ? repository.baseline(
                        plan.baselineSensor(),
                        week,
                        plan.refFrom(),
                        plan.refTo(),
                        year,
                        queryBbox)
                : new Baseline(null, 0);

        WeekFlags flags = repository.weekFlags(year, week, queryBbox);
        int nCellsAoi = repository.nCellsAoi(requested);

        int observedFireCells = selected == null ? 0 : selected.fireCells();
        int fireDays = selected == null ? 0 : selected.fireDays();
        int detectionCellDays = selected == null
                ? 0
                : repository.detectionCellDays(plan.observedSensor(), weekStart, weekEnd, queryBbox);

        Double rate = detectionCellDays > 0
                ? round(fireDays / (double) detectionCellDays, 3)
                : null;

        Double baselineValue = baseline.median();
        int referenceYears = baseline.nRef();
        String confidence = confidence(
                plan.anomalyAllowed(),
                observedFireCells,
                baselineValue,
                referenceYears);
        Double anomaly = "insufficient".equals(confidence)
                ? null
                : round(observedFireCells / baselineValue, 2);

        Map<String, Integer> fireCellsBySensor = new LinkedHashMap<>();
        Map<String, Integer> detectionsBySensor = new LinkedHashMap<>();
        Map<String, Double> frpBySensor = new LinkedHashMap<>();
        for (SensorWeek row : rows) {
            fireCellsBySensor.put(row.sensor(), row.fireCells());
            detectionsBySensor.put(row.sensor(), row.nDetections());
            frpBySensor.put(row.sensor(), row.frpSum());
        }

        boolean provisional = flags.hasNrt() || year >= 2026;
        String dataStatus = flags.rows() == 0 || selected == null ? "unknown" : "observed";

        return new WeeklyResponse(
                responseBbox,
                year,
                week,
                observedFireCells,
                baselineValue,
                anomaly,
                referenceYears,
                plan.refFrom() + "-" + plan.refTo(),
                plan.observedSensor(),
                plan.baselineSensor(),
                nCellsAoi,
                confidence,
                provisional,
                dataStatus,
                null,
                fireCellsBySensor,
                detectionsBySensor,
                frpBySensor,
                rate);
    }

    @Cacheable(value = "daily", key = "#bboxParam + '|' + #from + '|' + #to + '|' + #sensorParam + '|' + #limit")
    public List<DailyRow> getDaily(
            String bboxParam,
            LocalDate from,
            LocalDate to,
            String sensorParam,
            int limit) {
        validateRange(from, to, 366);
        int safeLimit = validateLimit(limit, 5000, 50000);
        Bbox queryBbox = queryBbox(bboxParam);
        String sensor = normalizeSensor(sensorParam, "ALL");

        List<DailyRow> rows = repository.daily(queryBbox, from, to, sensor, safeLimit);
        if (rows.isEmpty()) {
            return rows;
        }

        Map<String, Map<String, Integer>> hotspots = new HashMap<>();
        for (DailyHotspot row : repository.dailyHotspots(queryBbox, from, to, sensor)) {
            hotspots.computeIfAbsent(dayKey(row.day(), row.gridId()), key -> new LinkedHashMap<>())
                    .put(row.sensor(), row.hotspots());
        }

        List<DailyRow> enriched = new ArrayList<>(rows.size());
        for (DailyRow row : rows) {
            Map<String, Integer> bySensor = hotspots.getOrDefault(
                    dayKey(row.date(), row.gridId()), Map.of());
            int total = bySensor.values().stream().mapToInt(Integer::intValue).sum();
            enriched.add(new DailyRow(
                    row.date(),
                    row.gridId(),
                    row.cellLat(),
                    row.cellLon(),
                    row.fireDay(),
                    row.nDetections(),
                    row.sensors(),
                    total,
                    bySensor,
                    row.frpMaxMw(),
                    row.frpDensityMaxWm2(),
                    row.source(),
                    row.dataStatus()));
        }
        return enriched;
    }

    @Cacheable(value = "heatmap", key = "#bboxParam + '|' + #from + '|' + #to + '|' + #sensorParam + '|' + #limit")
    public List<HeatmapCell> getHeatmap(
            String bboxParam,
            LocalDate from,
            LocalDate to,
            String sensorParam,
            int limit) {
        validateRange(from, to, 366);
        int safeLimit = validateLimit(limit, 5000, 50000);
        Bbox queryBbox = queryBbox(bboxParam);
        String sensor = normalizeSensor(sensorParam, "ALL");

        List<HeatmapCell> rows = repository.heatmap(queryBbox, from, to, sensor, safeLimit);
        if (rows.isEmpty()) {
            return rows;
        }

        Map<String, Map<String, Integer>> hotspots = new HashMap<>();
        for (HeatmapHotspot row : repository.heatmapHotspots(queryBbox, from, to, sensor)) {
            hotspots.computeIfAbsent(row.gridId(), key -> new LinkedHashMap<>())
                    .put(row.sensor(), row.hotspots());
        }

        List<HeatmapCell> enriched = new ArrayList<>(rows.size());
        for (HeatmapCell row : rows) {
            Map<String, Integer> bySensor = hotspots.getOrDefault(row.gridId(), Map.of());
            int total = bySensor.values().stream().mapToInt(Integer::intValue).sum();
            enriched.add(new HeatmapCell(
                    row.gridId(),
                    row.cellLat(),
                    row.cellLon(),
                    row.fireCells(),
                    row.fireDays(),
                    row.nDetections(),
                    total,
                    bySensor,
                    row.anomaly(),
                    row.dataStatus()));
        }
        return enriched;
    }

    @Cacheable(value = "monthly", key = "#bboxParam + '|' + #from + '|' + #to + '|' + #sensorParam")
    public List<MonthlyCell> getMonthly(
            String bboxParam,
            LocalDate from,
            LocalDate to,
            String sensorParam) {
        validateRange(from, to, 10000);
        Bbox queryBbox = queryBbox(bboxParam);
        String sensor = normalizeSensor(sensorParam, "ALL");

        Map<String, MonthFire> fires = new HashMap<>();
        for (MonthFire row : repository.monthlyFire(queryBbox, from, to, sensor)) {
            fires.put(monthKey(row.year(), row.month()), row);
        }

        Map<String, Map<String, Integer>> detections = new HashMap<>();
        for (MonthSensorDetections row : repository.monthlyDetections(queryBbox, from, to, sensor)) {
            detections.computeIfAbsent(monthKey(row.year(), row.month()), key -> new LinkedHashMap<>())
                    .put(row.sensor(), row.nDetections());
        }

        Map<String, Map<String, Integer>> hotspotCounts = new HashMap<>();
        for (MonthlyHotspot row : repository.monthlyHotspots(queryBbox, from, to, sensor)) {
            hotspotCounts.computeIfAbsent(monthKey(row.year(), row.month()), key -> new LinkedHashMap<>())
                    .put(row.sensor(), row.hotspots());
        }

        List<MonthlyCell> result = new ArrayList<>();
        YearMonth cursor = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!cursor.isAfter(end)) {
            int year = cursor.getYear();
            int month = cursor.getMonthValue() - 1;
            String key = monthKey(year, month);
            MonthFire fire = fires.get(key);
            Map<String, Integer> bySensor = detections.getOrDefault(key, Map.of());
            Map<String, Integer> hotspotsBySensor = hotspotCounts.getOrDefault(key, Map.of());
            int nDetections = bySensor.values().stream().mapToInt(Integer::intValue).sum();
            int hotspots = hotspotsBySensor.values().stream().mapToInt(Integer::intValue).sum();
            boolean partial = cursor.atDay(1).isBefore(from) || cursor.atEndOfMonth().isAfter(to);
            String dataStatus = fire == null && bySensor.isEmpty() ? "unknown" : "observed";
            result.add(new MonthlyCell(
                    year,
                    month,
                    fire == null ? 0 : fire.fireCells(),
                    fire == null ? 0 : fire.fireDays(),
                    nDetections,
                    bySensor,
                    hotspots,
                    hotspotsBySensor,
                    partial,
                    dataStatus,
                    null,
                    null,
                    0,
                    "insufficient"));
            cursor = cursor.plusMonths(1);
        }
        return withMonthlyAnomaly(result, sensor);
    }

    /**
     * Calcula la anomalia mensual leave-one-out sobre la misma metrica
     * (fire_cells) y el mismo sensor de la serie observada.
     *
     * <p>Solo usa meses observados y completos como referencia, y respeta
     * la era del sensor: MODIS 2003-2011, VIIRS SNPP 2013-2025, NOAA-20
     * 2019-2025, NOAA-21 2024-2025. Para {@code ALL} se excluyen 2000-2002,
     * 2012 (transicion) y 2026 (NRT). Un mes parcial, sin datos o con menos
     * de 7 anos de referencia queda {@code insufficient}. La confianza es
     * {@code high} con 10+ anos y baseline >= 10; en el resto de los casos
     * validos es {@code low}.</p>
     */
    private List<MonthlyCell> withMonthlyAnomaly(List<MonthlyCell> cells, String sensor) {
        Map<Integer, List<MonthlyCell>> byMonth = new HashMap<>();
        for (MonthlyCell cell : cells) {
            if ("observed".equals(cell.dataStatus())
                    && !cell.partial()
                    && isMonthlyReferenceYear(cell.year(), sensor)) {
                byMonth.computeIfAbsent(cell.month(), key -> new ArrayList<>()).add(cell);
            }
        }

        List<MonthlyCell> enriched = new ArrayList<>(cells.size());
        for (MonthlyCell cell : cells) {
            List<Double> referenceValues = new ArrayList<>();
            for (MonthlyCell other : byMonth.getOrDefault(cell.month(), List.of())) {
                if (other.year() != cell.year()) {
                    referenceValues.add((double) other.fireCells());
                }
            }

            Double baseline = referenceValues.isEmpty()
                    ? null
                    : round(median(referenceValues), 1);
            double baselineValue = baseline == null ? 0 : baseline;
            boolean comparable = "observed".equals(cell.dataStatus())
                    && !cell.partial()
                    && isMonthlyAnomalyAllowed(cell.year(), sensor)
                    && cell.fireCells() >= 3
                    && referenceValues.size() >= 7
                    && baselineValue >= 3;

            Double anomaly = comparable
                    ? round(cell.fireCells() / baselineValue, 2)
                    : null;
            String confidence = !comparable
                    ? "insufficient"
                    : (referenceValues.size() >= 10 && baselineValue >= 10 ? "high" : "low");

            enriched.add(new MonthlyCell(
                    cell.year(),
                    cell.month(),
                    cell.fireCells(),
                    cell.fireDays(),
                    cell.nDetections(),
                    cell.detectionsBySensor(),
                    cell.hotspots(),
                    cell.hotspotsBySensor(),
                    cell.partial(),
                    cell.dataStatus(),
                    baseline,
                    anomaly,
                    referenceValues.size(),
                    confidence));
        }
        return enriched;
    }

    private boolean isMonthlyReferenceYear(int year, String sensor) {
        return switch (sensor) {
            case "MODIS" -> year >= 2003 && year <= 2011;
            case "VIIRS_SNPP" -> year >= 2013 && year <= 2025;
            case "VIIRS_NOAA20" -> year >= 2019 && year <= 2025;
            case "VIIRS_NOAA21" -> year >= 2024 && year <= 2025;
            default -> year >= 2003 && year <= 2025 && year != 2012;
        };
    }

    private boolean isMonthlyAnomalyAllowed(int year, String sensor) {
        return switch (sensor) {
            case "MODIS" -> year >= 2003;
            case "VIIRS_SNPP" -> year >= 2013;
            case "VIIRS_NOAA20" -> year >= 2019;
            case "VIIRS_NOAA21" -> year >= 2024;
            default -> year >= 2003 && year != 2012;
        };
    }

    @Cacheable(value = "trend", key = "#bboxParam + '|' + #from + '|' + #to + '|' + #sensorParam")
    public List<TrendRow> getTrend(
            String bboxParam,
            LocalDate from,
            LocalDate to,
            String sensorParam) {
        validateRange(from, to, 10000);
        Bbox queryBbox = queryBbox(bboxParam);

        Map<String, Integer> annual = new HashMap<>();
        for (AnnualSensor row : repository.annualBySensor(queryBbox, 2003, 2026)) {
            annual.put(sensorYearKey(row.sensor(), row.year()), row.fireCells());
        }

        List<TrendRow> result = new ArrayList<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            SensorPlan plan = resolvePlan(year, sensorParam);
            int observed = annual.getOrDefault(sensorYearKey(plan.observedSensor(), year), 0);

            List<Double> refValues = new ArrayList<>();
            for (int refYear = plan.refFrom(); refYear <= plan.refTo(); refYear++) {
                if (refYear == year) {
                    continue;
                }
                Integer value = annual.get(sensorYearKey(plan.baselineSensor(), refYear));
                if (value != null) {
                    refValues.add(value.doubleValue());
                }
            }

            Double baseline = plan.anomalyAllowed() ? median(refValues) : null;
            int referenceYears = plan.anomalyAllowed() ? refValues.size() : 0;
            String confidence = confidence(plan.anomalyAllowed(), observed, baseline, referenceYears);
            Double anomaly = "insufficient".equals(confidence)
                    ? null
                    : round(observed / baseline, 2);

            result.add(new TrendRow(
                    year,
                    observed,
                    baseline,
                    anomaly,
                    referenceYears,
                    confidence,
                    year >= 2026,
                    plan.observedSensor(),
                    plan.baselineSensor()));
        }
        return result;
    }

    public String exportCsv(
            String bboxParam,
            LocalDate from,
            LocalDate to,
            String sensorParam,
            int limit) {
        validateRange(from, to, 10000);
        int safeLimit = validateLimit(limit, 50000, 50000);
        Bbox queryBbox = queryBbox(bboxParam);
        String sensor = normalizeSensor(sensorParam, "ALL");

        int fromYear = from.get(IsoFields.WEEK_BASED_YEAR);
        int fromWeek = from.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int toYear = to.get(IsoFields.WEEK_BASED_YEAR);
        int toWeek = to.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        List<WeeklyExportRow> rows = repository.weeklyExport(
                queryBbox, fromYear, fromWeek, toYear, toWeek, sensor, safeLimit);

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("isoyear,week_of_year,grid_id,cell_lat,cell_lon,sensor,")
                .append("fire_cells,fire_days,n_detections,frp_sum_mw\n");
        for (WeeklyExportRow row : rows) {
            csv.append(row.isoyear()).append(',')
                    .append(row.weekOfYear()).append(',')
                    .append(csv(row.gridId())).append(',')
                    .append(row.cellLat()).append(',')
                    .append(row.cellLon()).append(',')
                    .append(csv(row.sensor())).append(',')
                    .append(row.fireCells()).append(',')
                    .append(row.fireDays()).append(',')
                    .append(row.nDetections()).append(',')
                    .append(row.frpSumMw() == null ? "" : row.frpSumMw()).append('\n');
        }
        return csv.toString();
    }

    private Bbox queryBbox(String bboxParam) {
        Bbox requested = parseBbox(bboxParam);
        return requested == null ? FULL_EXTENT : requested;
    }

    private String normalizeSensor(String sensorParam, String defaultValue) {
        String value = sensorParam == null || sensorParam.isBlank()
                ? defaultValue
                : sensorParam.trim().toUpperCase(Locale.ROOT);
        if ("AUTO".equals(value)) {
            return "ALL";
        }
        if (!Set.of("ALL", "MODIS", "VIIRS_SNPP", "VIIRS_NOAA20", "VIIRS_NOAA21").contains(value)) {
            throw new IllegalArgumentException(
                    "Sensor invalido: " + sensorParam
                            + ". Use ALL, AUTO, MODIS, VIIRS_SNPP, VIIRS_NOAA20 o VIIRS_NOAA21.");
        }
        return value;
    }

    private void validateRange(LocalDate from, LocalDate to, int maxDays) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from y to son requeridos.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from debe ser anterior o igual a to.");
        }
        if (from.isBefore(LocalDate.of(2000, 1, 1)) || to.isAfter(LocalDate.of(2026, 12, 31))) {
            throw new IllegalArgumentException("El rango debe estar entre 2000-01-01 y 2026-12-31.");
        }
        if (to.toEpochDay() - from.toEpochDay() > maxDays) {
            throw new IllegalArgumentException("El rango supera el maximo de " + maxDays + " dias.");
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

    private String monthKey(int year, int month) {
        return year + "-" + month;
    }

    private String dayKey(LocalDate day, String gridId) {
        return day + "|" + gridId;
    }

    private String sensorYearKey(String sensor, int year) {
        return sensor + "|" + year;
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

    private Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0
                : sorted.get(mid);
    }

    private SensorPlan resolvePlan(int year, String sensorParam) {
        String requested = sensorParam == null || sensorParam.isBlank()
                ? "AUTO"
                : sensorParam.trim().toUpperCase(Locale.ROOT);

        if ("ALL".equals(requested)) {
            throw new IllegalArgumentException(
                    "El calendario semanal requiere AUTO o un sensor especifico; ALL no es valido para la anomalia.");
        }

        if ("AUTO".equals(requested)) {
            if (year >= 2013) {
                return new SensorPlan("VIIRS_SNPP", "VIIRS_SNPP", 2013, 2025, true);
            }
            if (year == 2012) {
                return new SensorPlan("MODIS", "MODIS", 2003, 2011, false);
            }
            if (year >= 2003) {
                return new SensorPlan("MODIS", "MODIS", 2003, 2011, true);
            }
            return new SensorPlan("MODIS", "MODIS", 2003, 2011, false);
        }

        return switch (requested) {
            case "MODIS" -> new SensorPlan("MODIS", "MODIS", 2003, 2011, year >= 2003);
            case "VIIRS_SNPP" -> new SensorPlan("VIIRS_SNPP", "VIIRS_SNPP", 2013, 2025, year >= 2013);
            case "VIIRS_NOAA20" -> new SensorPlan("VIIRS_NOAA20", "VIIRS_NOAA20", 2019, 2025, year >= 2019);
            case "VIIRS_NOAA21" -> new SensorPlan("VIIRS_NOAA21", "VIIRS_NOAA21", 2024, 2025, year >= 2024);
            default -> throw new IllegalArgumentException(
                    "Sensor invalido: " + sensorParam
                            + ". Use AUTO, MODIS, VIIRS_SNPP, VIIRS_NOAA20 o VIIRS_NOAA21.");
        };
    }

    private String confidence(boolean anomalyAllowed, int observed, Double baseline, int referenceYears) {
        if (!anomalyAllowed || baseline == null || referenceYears < 7 || observed < 3 || baseline < 3) {
            return "insufficient";
        }
        if (referenceYears >= 10 && baseline >= 10) {
            return "high";
        }
        return "low";
    }

    private Bbox parseBbox(String bboxParam) {
        if (bboxParam == null || bboxParam.isBlank()) {
            return null;
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

    private void validateYear(int year) {
        if (year < 2000 || year > 2026) {
            throw new IllegalArgumentException("year debe estar entre 2000 y 2026.");
        }
    }

    private void validateWeek(int week) {
        if (week < 1 || week > 53) {
            throw new IllegalArgumentException("week debe estar entre 1 y 53.");
        }
    }

    private LocalDate weekStart(int year, int week) {
        LocalDate jan4 = LocalDate.of(year, 1, 4);
        LocalDate week1Monday = jan4.minusDays(jan4.getDayOfWeek().getValue() - 1L);
        LocalDate start = week1Monday.plusWeeks(week - 1L);
        if (start.get(IsoFields.WEEK_BASED_YEAR) != year
                || start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != week) {
            throw new IllegalArgumentException("Semana ISO invalida para el anio " + year + ": " + week);
        }
        return start;
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private record SensorPlan(
            String observedSensor,
            String baselineSensor,
            int refFrom,
            int refTo,
            boolean anomalyAllowed
    ) {
    }
}
