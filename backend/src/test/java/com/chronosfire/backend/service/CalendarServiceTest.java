package com.chronosfire.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.CsvExport;
import com.chronosfire.backend.dto.DailyRow;
import com.chronosfire.backend.dto.HeatmapCell;
import com.chronosfire.backend.dto.MonthlyCell;
import com.chronosfire.backend.dto.TrendRow;
import com.chronosfire.backend.dto.WeeklyResponse;
import com.chronosfire.backend.repository.CalendarRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarServiceTest {

    @Test
    void reproducesDelta2020Week40WithSnppOnly() {
        StubRepository repository = new StubRepository();
        repository.rows = List.of(
                new CalendarRepository.SensorWeek("MODIS", 20, 40, 120, 500.0),
                new CalendarRepository.SensorWeek("VIIRS_SNPP", 66, 178, 2355, 1000.0),
                new CalendarRepository.SensorWeek("VIIRS_NOAA20", 40, 90, 608, 450.0));
        repository.baseline = new CalendarRepository.Baseline(10.0, 12);
        repository.flags = new CalendarRepository.WeekFlags(10, false);
        repository.nCells = 331;
        repository.detectionCellDays = 300;

        WeeklyResponse response = new CalendarService(repository)
                .getWeekly("-61,-34,-59.5,-32.5", 2020, 40, "AUTO");

        assertEquals(66, response.observedFireCells());
        assertEquals(10.0, response.baselineFireCells());
        assertEquals(6.6, response.anomaly());
        assertEquals(12, response.referenceYears());
        assertEquals("2013-2025", response.referenceWindow());
        assertEquals("VIIRS_SNPP", response.observedSensor());
        assertEquals("VIIRS_SNPP", response.baselineSensor());
        assertEquals(331, response.nCellsAoi());
        assertEquals("high", response.confidence());
        assertFalse(response.provisional());
        assertEquals("observed", response.dataStatus());
        assertNull(response.nDataYears());
        assertEquals(66, response.fireCellsBySensor().get("VIIRS_SNPP"));
        assertEquals(2355, response.detectionsBySensor().get("VIIRS_SNPP"));
        assertEquals(0.593, response.fireDayRateGivenDetection());
    }

    @Test
    void autoIn2012UsesModisWithoutAnomaly() {
        StubRepository repository = new StubRepository();
        repository.rows = List.of(new CalendarRepository.SensorWeek("MODIS", 30, 40, 100, 200.0));
        repository.flags = new CalendarRepository.WeekFlags(5, false);
        repository.nCells = 100;

        WeeklyResponse response = new CalendarService(repository)
                .getWeekly(null, 2012, 1, "AUTO");

        assertEquals("MODIS", response.observedSensor());
        assertNull(response.baselineFireCells());
        assertNull(response.anomaly());
        assertEquals("insufficient", response.confidence());
        assertEquals("2003-2011", response.referenceWindow());
    }

    @Test
    void monthlyFillsMissingMonthsAndMarksPartialRanges() {
        StubRepository repository = new StubRepository();
        repository.monthlyFire = List.of(
                new CalendarRepository.MonthFire(2020, 0, 10, 20, 100),
                new CalendarRepository.MonthFire(2020, 2, 5, 8, 50));
        repository.monthlyDetections = List.of(
                new CalendarRepository.MonthSensorDetections(2020, 0, "VIIRS_SNPP", 100),
                new CalendarRepository.MonthSensorDetections(2020, 2, "VIIRS_SNPP", 50));

        List<MonthlyCell> months = new CalendarService(repository)
                .getMonthly("-61,-34,-59.5,-32.5", LocalDate.parse("2020-01-01"), LocalDate.parse("2020-03-31"), "ALL");

        assertEquals(3, months.size());
        assertEquals(10, months.get(0).fireCells());
        assertEquals(20, months.get(0).fireDays());
        assertEquals(100, months.get(0).nDetections());
        assertFalse(months.get(0).partial());
        assertEquals("unknown", months.get(1).dataStatus());
        assertEquals(0, months.get(1).fireCells());
        assertEquals("observed", months.get(2).dataStatus());
        assertEquals("ALL", repository.monthlyFireSensor);
    }

    @Test
    void monthlyReportsHotspotsSeparateFromRawDetections() {
        StubRepository repository = new StubRepository();
        repository.monthlyFire = List.of(
                new CalendarRepository.MonthFire(2020, 8, 66, 78, 8689));
        repository.monthlyDetections = List.of(
                new CalendarRepository.MonthSensorDetections(2020, 8, "VIIRS_SNPP", 8098));
        repository.monthlyHotspots = List.of(
                new CalendarRepository.MonthlyHotspot(2020, 8, "VIIRS_SNPP", 6628));

        List<MonthlyCell> months = new CalendarService(repository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2020-09-01"),
                        LocalDate.parse("2020-09-30"),
                        "VIIRS_SNPP");

        MonthlyCell month = months.get(0);
        assertEquals(8098, month.nDetections());
        assertEquals(6628, month.hotspots());
        assertEquals(6628, month.hotspotsBySensor().get("VIIRS_SNPP"));
        assertTrue(month.hotspots() <= month.nDetections());
        assertEquals("VIIRS_SNPP", repository.monthlyHotspotsSensor);
    }

    @Test
    void monthlyComputesLeaveOneOutAnomalyAndHighConfidence() {
        StubRepository repository = new StubRepository();
        repository.monthlyFire = septemberRows(2013, 2023, 10, 2023, 100);

        List<MonthlyCell> months = new CalendarService(repository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2013-09-01"),
                        LocalDate.parse("2023-09-30"),
                        "ALL");

        MonthlyCell peak = month(months, 2023, 8);
        assertEquals(10.0, peak.baselineFireCells());
        assertEquals(10.0, peak.anomaly());
        assertEquals(10, peak.referenceYears());
        assertEquals("high", peak.confidence());

        MonthlyCell base = month(months, 2013, 8);
        assertEquals(10.0, base.baselineFireCells());
        assertEquals(1.0, base.anomaly());
        assertEquals("high", base.confidence());
    }

    @Test
    void monthlyAnomalyIsLowWithSevenToNineReferenceYears() {
        StubRepository repository = new StubRepository();
        repository.monthlyFire = septemberRows(2014, 2023, 10, 2023, 100);

        List<MonthlyCell> months = new CalendarService(repository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2014-09-01"),
                        LocalDate.parse("2023-09-30"),
                        "ALL");

        MonthlyCell peak = month(months, 2023, 8);
        assertEquals(9, peak.referenceYears());
        assertEquals(10.0, peak.baselineFireCells());
        assertEquals(10.0, peak.anomaly());
        assertEquals("low", peak.confidence());
    }

    @Test
    void monthlyAnomalyIsInsufficientForTransitionYear2012() {
        StubRepository repository = new StubRepository();
        repository.monthlyFire = septemberRows(2003, 2012, 10, 2012, 100);

        List<MonthlyCell> months = new CalendarService(repository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2003-09-01"),
                        LocalDate.parse("2012-09-30"),
                        "ALL");

        MonthlyCell transition = month(months, 2012, 8);
        assertEquals(9, transition.referenceYears());
        assertNull(transition.anomaly());
        assertEquals("insufficient", transition.confidence());
    }

    @Test
    void monthlyAnomalyExcludes2026FromReferenceYears() {
        StubRepository repository = new StubRepository();
        repository.monthlyFire = septemberRows(2013, 2026, 10, 2020, 100);

        List<MonthlyCell> months = new CalendarService(repository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2013-09-01"),
                        LocalDate.parse("2026-09-30"),
                        "ALL");

        MonthlyCell peak = month(months, 2020, 8);
        assertEquals(12, peak.referenceYears());
        assertEquals(10.0, peak.baselineFireCells());
        assertEquals(10.0, peak.anomaly());
        assertEquals("high", peak.confidence());
    }

    @Test
    void monthlyAnomalyIsInsufficientWithFewReferenceYears() {
        StubRepository repository = new StubRepository();
        repository.monthlyFire = septemberRows(2018, 2020, 10, 2020, 100);

        List<MonthlyCell> months = new CalendarService(repository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2018-09-01"),
                        LocalDate.parse("2020-09-30"),
                        "ALL");

        MonthlyCell peak = month(months, 2020, 8);
        assertEquals(2, peak.referenceYears());
        assertEquals(10.0, peak.baselineFireCells());
        assertNull(peak.anomaly());
        assertEquals("insufficient", peak.confidence());
    }

    @Test
    void monthlyAnomalyIsInsufficientForPartialOrZeroBaseline() {
        StubRepository partialRepository = new StubRepository();
        partialRepository.monthlyFire = septemberRows(2013, 2023, 10, 2023, 100);

        MonthlyCell partial = month(new CalendarService(partialRepository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2023-09-15"),
                        LocalDate.parse("2023-09-30"),
                        "ALL"), 2023, 8);

        assertTrue(partial.partial());
        assertNull(partial.anomaly());
        assertEquals(0, partial.referenceYears());
        assertEquals("insufficient", partial.confidence());

        StubRepository zeroRepository = new StubRepository();
        zeroRepository.monthlyFire = septemberRows(2013, 2023, 0, 2023, 100);

        MonthlyCell zero = month(new CalendarService(zeroRepository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2013-09-01"),
                        LocalDate.parse("2023-09-30"),
                        "ALL"), 2023, 8);

        assertEquals(10, zero.referenceYears());
        assertEquals(0.0, zero.baselineFireCells());
        assertNull(zero.anomaly());
        assertEquals("insufficient", zero.confidence());
    }

    @Test
    void heatmapEnrichesCellsWithHarmonizedHotspots() {
        StubRepository repository = new StubRepository();
        repository.heatmapRows = List.of(new HeatmapCell(
                "0.05:-660:-1200",
                -33.0,
                -60.0,
                1,
                4,
                10,
                0,
                Map.of(),
                null,
                "observed"));
        repository.heatmapHotspots = List.of(
                new CalendarRepository.HeatmapHotspot("0.05:-660:-1200", "MODIS", 2),
                new CalendarRepository.HeatmapHotspot("0.05:-660:-1200", "VIIRS_SNPP", 5));

        List<HeatmapCell> cells = new CalendarService(repository)
                .getHeatmap(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2020-09-01"),
                        LocalDate.parse("2020-09-30"),
                        "ALL",
                        5000);

        assertEquals(1, cells.size());
        assertEquals(7, cells.get(0).hotspots());
        assertEquals(2, cells.get(0).hotspotsBySensor().get("MODIS"));
        assertEquals(5, cells.get(0).hotspotsBySensor().get("VIIRS_SNPP"));
        assertEquals(10, cells.get(0).nDetections());
        assertEquals("ALL", repository.heatmapHotspotsSensor);
    }

    @Test
    void dailyEnrichesRowsWithHarmonizedHotspots() {
        StubRepository repository = new StubRepository();
        LocalDate day = LocalDate.parse("2020-09-15");
        repository.dailyRows = List.of(new DailyRow(
                day,
                "0.05:-660:-1200",
                -33.0,
                -60.0,
                true,
                10,
                List.of("MODIS", "VIIRS_SNPP"),
                0,
                Map.of(),
                50.0,
                1.2,
                null,
                "observed"));
        repository.dailyHotspots = List.of(
                new CalendarRepository.DailyHotspot(day, "0.05:-660:-1200", "MODIS", 2),
                new CalendarRepository.DailyHotspot(day, "0.05:-660:-1200", "VIIRS_SNPP", 5));

        List<DailyRow> rows = new CalendarService(repository)
                .getDaily(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2020-09-01"),
                        LocalDate.parse("2020-09-30"),
                        "ALL",
                        10);

        assertEquals(1, rows.size());
        assertEquals(7, rows.get(0).hotspots());
        assertEquals(2, rows.get(0).hotspotsBySensor().get("MODIS"));
        assertEquals(5, rows.get(0).hotspotsBySensor().get("VIIRS_SNPP"));
        assertEquals(10, rows.get(0).nDetections());
        assertEquals("ALL", repository.dailyHotspotsSensor);
    }

    @Test
    void monthlyForwardsSensorToRepository() {
        StubRepository repository = new StubRepository();
        repository.monthlyFire = List.of(
                new CalendarRepository.MonthFire(2020, 8, 66, 78, 1242));
        repository.monthlyDetections = List.of(
                new CalendarRepository.MonthSensorDetections(2020, 8, "VIIRS_SNPP", 1242));

        List<MonthlyCell> months = new CalendarService(repository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2020-09-01"),
                        LocalDate.parse("2020-09-30"),
                        "VIIRS_SNPP");

        assertEquals(1, months.size());
        assertEquals(66, months.get(0).fireCells());
        assertEquals("VIIRS_SNPP", repository.monthlyFireSensor);
    }

    @Test
    void monthlyNormalizesAutoToAll() {
        StubRepository repository = new StubRepository();

        new CalendarService(repository)
                .getMonthly(
                        "-61,-34,-59.5,-32.5",
                        LocalDate.parse("2020-09-01"),
                        LocalDate.parse("2020-09-30"),
                        "AUTO");

        assertEquals("ALL", repository.monthlyFireSensor);
    }

    @Test
    void monthlyRejectsInvalidSensor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalendarService(new StubRepository())
                        .getMonthly(
                                "-61,-34,-59.5,-32.5",
                                LocalDate.parse("2020-09-01"),
                                LocalDate.parse("2020-09-30"),
                                "VIIRS_BAD"));
    }

    @Test
    void trendComputesAnnualAnomalyWithLeaveOneOut() {
        StubRepository repository = new StubRepository();
        repository.annual = List.of(
                new CalendarRepository.AnnualSensor(2013, "VIIRS_SNPP", 10),
                new CalendarRepository.AnnualSensor(2014, "VIIRS_SNPP", 10),
                new CalendarRepository.AnnualSensor(2015, "VIIRS_SNPP", 10),
                new CalendarRepository.AnnualSensor(2016, "VIIRS_SNPP", 10),
                new CalendarRepository.AnnualSensor(2017, "VIIRS_SNPP", 10),
                new CalendarRepository.AnnualSensor(2018, "VIIRS_SNPP", 10),
                new CalendarRepository.AnnualSensor(2019, "VIIRS_SNPP", 10),
                new CalendarRepository.AnnualSensor(2020, "VIIRS_SNPP", 100),
                new CalendarRepository.AnnualSensor(2021, "VIIRS_SNPP", 20),
                new CalendarRepository.AnnualSensor(2022, "VIIRS_SNPP", 20),
                new CalendarRepository.AnnualSensor(2023, "VIIRS_SNPP", 20),
                new CalendarRepository.AnnualSensor(2024, "VIIRS_SNPP", 20),
                new CalendarRepository.AnnualSensor(2025, "VIIRS_SNPP", 20));

        List<TrendRow> trend = new CalendarService(repository)
                .getTrend("-61,-34,-59.5,-32.5", LocalDate.parse("2020-01-01"), LocalDate.parse("2020-12-31"), "AUTO");

        assertEquals(1, trend.size());
        TrendRow row = trend.get(0);
        assertEquals(2020, row.year());
        assertEquals(100, row.fireCells());
        assertEquals(10.0, row.baselineFireCells());
        assertEquals(10.0, row.anomaly());
        assertEquals(12, row.referenceYears());
        assertEquals("high", row.confidence());
        assertEquals("VIIRS_SNPP", row.observedSensor());
    }

    @Test
    void exportsWeeklyCsv() {
        StubRepository repository = new StubRepository();
        repository.weeklyRows = List.of(new CalendarRepository.WeeklyExportRow(
                2020, 40, "0.05:-660:-1200", -33.0, -60.0,
                "VIIRS_SNPP", 1, 3, 12, 45.5));

        CsvExport export = new CalendarService(repository)
                .exportCsv("-61,-34,-59.5,-32.5", LocalDate.parse("2020-09-28"), LocalDate.parse("2020-10-04"), "ALL", 10);

        assertTrue(export.csv().contains("isoyear,week_of_year"));
        assertTrue(export.csv().contains("2020,40,0.05:-660:-1200"));
        assertTrue(export.csv().contains("VIIRS_SNPP,1,3,12,45.5"));
        assertEquals(1, export.rows());
        assertFalse(export.truncated());
    }

    @Test
    void weeklyCsvTruncatesAtLimit() {
        StubRepository repository = new StubRepository();
        repository.weeklyRows = List.of(
                new CalendarRepository.WeeklyExportRow(2020, 40, "a", -33.0, -60.0, "MODIS", 1, 1, 1, null),
                new CalendarRepository.WeeklyExportRow(2020, 40, "b", -33.1, -60.1, "MODIS", 1, 1, 1, null),
                new CalendarRepository.WeeklyExportRow(2020, 40, "c", -33.2, -60.2, "MODIS", 1, 1, 1, null));

        CsvExport export = new CalendarService(repository)
                .exportCsv("-61,-34,-59.5,-32.5", LocalDate.parse("2020-09-28"), LocalDate.parse("2020-10-04"), "ALL", 2);

        assertTrue(export.truncated());
        assertEquals(2, export.rows());
        assertEquals(2, export.limit());
        assertTrue(export.csv().contains(",a,"));
        assertTrue(export.csv().contains(",b,"));
        assertFalse(export.csv().contains(",c,"));
    }

    @Test
    void rejectsAllSensorForAnomaly() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new CalendarService(new StubRepository()).getWeekly(null, 2020, 40, "ALL"));
        assertTrue(error.getMessage().contains("ALL"));
    }

    @Test
    void rejectsInvalidIsoWeek() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalendarService(new StubRepository()).getWeekly(null, 2021, 53, "AUTO"));
    }

    @Test
    void rejectsInvalidBbox() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalendarService(new StubRepository()).getWeekly("1,2,0,3", 2020, 40, "AUTO"));
    }

    @Test
    void rejectsInvalidYear() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalendarService(new StubRepository()).getWeekly(null, 1999, 1, "AUTO"));
    }

    @Test
    void rejectsRangeTooLongForDaily() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalendarService(new StubRepository()).getDaily(
                        null,
                        LocalDate.parse("2020-01-01"),
                        LocalDate.parse("2021-12-31"),
                        "ALL",
                        10));
    }

    @Test
    void rejectsLimitAboveMaxForDaily() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalendarService(new StubRepository()).getDaily(
                        null,
                        LocalDate.parse("2020-01-01"),
                        LocalDate.parse("2020-01-31"),
                        "ALL",
                        50001));
    }

    @Test
    void autoIn2013UsesSnppAndSensorEraBaseline() {
        StubRepository repository = new StubRepository();
        repository.rows = List.of(new CalendarRepository.SensorWeek(
                "VIIRS_SNPP", 20, 30, 100, 200.0));
        repository.baseline = new CalendarRepository.Baseline(10.0, 12);
        repository.flags = new CalendarRepository.WeekFlags(1, false);
        repository.nCells = 100;
        repository.detectionCellDays = 50;

        WeeklyResponse response = new CalendarService(repository)
                .getWeekly(null, 2013, 1, "AUTO");

        assertEquals("VIIRS_SNPP", response.observedSensor());
        assertEquals("VIIRS_SNPP", response.baselineSensor());
        assertEquals("2013-2025", response.referenceWindow());
        assertEquals(2.0, response.anomaly());
        assertEquals("high", response.confidence());
        assertFalse(response.provisional());
    }

    @Test
    void autoIn2026IsProvisionalAndUses2013To2025Baseline() {
        StubRepository repository = new StubRepository();
        repository.rows = List.of(new CalendarRepository.SensorWeek(
                "VIIRS_SNPP", 20, 30, 100, 200.0));
        repository.baseline = new CalendarRepository.Baseline(10.0, 12);
        repository.flags = new CalendarRepository.WeekFlags(1, true);
        repository.nCells = 100;

        WeeklyResponse response = new CalendarService(repository)
                .getWeekly(null, 2026, 1, "AUTO");

        assertEquals("VIIRS_SNPP", response.observedSensor());
        assertEquals("2013-2025", response.referenceWindow());
        assertTrue(response.provisional());
        assertEquals("observed", response.dataStatus());
    }

    private static List<CalendarRepository.MonthFire> septemberRows(
            int fromYear,
            int toYear,
            int base,
            int peakYear,
            int peak) {
        List<CalendarRepository.MonthFire> rows = new ArrayList<>();
        for (int year = fromYear; year <= toYear; year++) {
            int value = year == peakYear ? peak : base;
            rows.add(new CalendarRepository.MonthFire(year, 8, value, value, 0));
        }
        return rows;
    }

    private static MonthlyCell month(List<MonthlyCell> months, int year, int month) {
        return months.stream()
                .filter(cell -> cell.year() == year && cell.month() == month)
                .findFirst()
                .orElseThrow();
    }

    private static class StubRepository extends CalendarRepository {

        private List<SensorWeek> rows = List.of();
        private Baseline baseline = new Baseline(null, 0);
        private WeekFlags flags = new WeekFlags(0, false);
        private int nCells = 0;
        private int detectionCellDays = 0;
        private List<MonthFire> monthlyFire = List.of();
        private String monthlyFireSensor;
        private List<MonthSensorDetections> monthlyDetections = List.of();
        private List<MonthlyHotspot> monthlyHotspots = List.of();
        private String monthlyHotspotsSensor;
        private List<DailyRow> dailyRows = List.of();
        private List<DailyHotspot> dailyHotspots = List.of();
        private String dailyHotspotsSensor;
        private List<HeatmapCell> heatmapRows = List.of();
        private List<HeatmapHotspot> heatmapHotspots = List.of();
        private String heatmapHotspotsSensor;
        private List<AnnualSensor> annual = List.of();
        private List<WeeklyExportRow> weeklyRows = List.of();

        StubRepository() {
            super(null);
        }

        @Override
        public List<SensorWeek> bySensor(int year, int week, Bbox bbox) {
            return rows;
        }

        @Override
        public Baseline baseline(
                String sensor,
                int week,
                int refFrom,
                int refTo,
                int excludeYear,
                Bbox bbox) {
            return baseline;
        }

        @Override
        public int nCellsAoi(Bbox bbox) {
            return nCells;
        }

        @Override
        public WeekFlags weekFlags(int year, int week, Bbox bbox) {
            return flags;
        }

        @Override
        public int detectionCellDays(String sensor, LocalDate from, LocalDate to, Bbox bbox) {
            return detectionCellDays;
        }

        @Override
        public List<MonthFire> monthlyFire(Bbox bbox, LocalDate from, LocalDate to, String sensor) {
            monthlyFireSensor = sensor;
            return monthlyFire;
        }

        @Override
        public List<MonthSensorDetections> monthlyDetections(
                Bbox bbox,
                LocalDate from,
                LocalDate to,
                String sensor) {
            return monthlyDetections;
        }

        @Override
        public List<MonthlyHotspot> monthlyHotspots(
                Bbox bbox,
                LocalDate from,
                LocalDate to,
                String sensor) {
            monthlyHotspotsSensor = sensor;
            return monthlyHotspots;
        }

        @Override
        public List<DailyRow> daily(
                Bbox bbox,
                LocalDate from,
                LocalDate to,
                String sensor,
                int limit) {
            return dailyRows;
        }

        @Override
        public List<DailyHotspot> dailyHotspots(
                Bbox bbox,
                LocalDate from,
                LocalDate to,
                String sensor) {
            dailyHotspotsSensor = sensor;
            return dailyHotspots;
        }

        @Override
        public List<HeatmapCell> heatmap(
                Bbox bbox,
                LocalDate from,
                LocalDate to,
                String sensor,
                int limit) {
            return heatmapRows;
        }

        @Override
        public List<HeatmapHotspot> heatmapHotspots(
                Bbox bbox,
                LocalDate from,
                LocalDate to,
                String sensor) {
            heatmapHotspotsSensor = sensor;
            return heatmapHotspots;
        }

        @Override
        public List<AnnualSensor> annualBySensor(Bbox bbox, int fromYear, int toYear) {
            return annual;
        }

        @Override
        public List<WeeklyExportRow> weeklyExport(
                Bbox bbox,
                int fromYear,
                int fromWeek,
                int toYear,
                int toWeek,
                String sensor,
                int limit) {
            return weeklyRows;
        }
    }
}
