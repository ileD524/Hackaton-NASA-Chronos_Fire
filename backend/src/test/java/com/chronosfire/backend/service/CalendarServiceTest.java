package com.chronosfire.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.MonthlyCell;
import com.chronosfire.backend.dto.TrendRow;
import com.chronosfire.backend.dto.WeeklyResponse;
import com.chronosfire.backend.repository.CalendarRepository;
import java.time.LocalDate;
import java.util.List;
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

        String csv = new CalendarService(repository)
                .exportCsv("-61,-34,-59.5,-32.5", LocalDate.parse("2020-09-28"), LocalDate.parse("2020-10-04"), "ALL", 10);

        assertTrue(csv.contains("isoyear,week_of_year"));
        assertTrue(csv.contains("2020,40,0.05:-660:-1200"));
        assertTrue(csv.contains("VIIRS_SNPP,1,3,12,45.5"));
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

    private static class StubRepository extends CalendarRepository {

        private List<SensorWeek> rows = List.of();
        private Baseline baseline = new Baseline(null, 0);
        private WeekFlags flags = new WeekFlags(0, false);
        private int nCells = 0;
        private int detectionCellDays = 0;
        private List<MonthFire> monthlyFire = List.of();
        private List<MonthSensorDetections> monthlyDetections = List.of();
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
        public List<MonthFire> monthlyFire(Bbox bbox, LocalDate from, LocalDate to) {
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
