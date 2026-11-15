package com.chronosfire.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chronosfire.backend.dto.CsvExport;
import com.chronosfire.backend.dto.Detection;
import com.chronosfire.backend.repository.DetectionFilter;
import com.chronosfire.backend.repository.DetectionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectionServiceTest {

    private static final String BBOX = "-61,-34,-59.5,-32.5";
    private static final LocalDate FROM = LocalDate.parse("2020-09-01");
    private static final LocalDate TO = LocalDate.parse("2020-09-30");

    @Test
    void exportCsvTruncatesAtLimit() {
        StubRepository repository = new StubRepository();
        repository.rows = List.of(
                detection(-33.1),
                detection(-33.2),
                detection(-33.3));

        CsvExport export = new DetectionService(repository).exportCsv(
                BBOX, FROM, TO, "ALL", null, null, null, true, 2);

        assertTrue(export.truncated());
        assertEquals(2, export.rows());
        assertEquals(2, export.limit());
        assertTrue(export.csv().contains("-33.1"));
        assertTrue(export.csv().contains("-33.2"));
        assertFalse(export.csv().contains("-33.3"));
    }

    @Test
    void exportCsvAllowsTheDefaultHistoryRange() {
        StubRepository repository = new StubRepository();
        repository.rows = List.of(detection(-33.1));

        CsvExport export = new DetectionService(repository).exportCsv(
                BBOX,
                LocalDate.parse("2012-01-01"),
                LocalDate.parse("2026-11-14"),
                "ALL",
                null,
                null,
                null,
                true,
                50000);

        assertFalse(export.truncated());
        assertEquals(1, export.rows());
        assertTrue(export.csv().contains("-33.1"));
    }

    @Test
    void pageRejectsRangeLongerThanThePaginationLimit() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new DetectionService(new StubRepository()).getPage(
                        BBOX,
                        LocalDate.parse("2012-01-01"),
                        LocalDate.parse("2026-11-14"),
                        "ALL",
                        null,
                        null,
                        null,
                        true,
                        10,
                        0));

        assertTrue(error.getMessage().contains("3660"));
    }

    @Test
    void pageRejectsInvalidFireType() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new DetectionService(new StubRepository()).getPage(
                        BBOX, FROM, TO, "ALL", null, 5, null, true, 10, 0));

        assertTrue(error.getMessage().contains("type"));
    }

    private static Detection detection(double latitude) {
        return new Detection(
                "MODIS",
                "SP",
                Instant.parse("2020-09-04T12:00:00Z"),
                LocalDate.parse("2020-09-04"),
                latitude,
                -60.3,
                "80",
                80,
                "high",
                0,
                true,
                false,
                12.5,
                0.5,
                1.0,
                null,
                "6.1");
    }

    private static class StubRepository extends DetectionRepository {

        private List<Detection> rows = List.of();

        StubRepository() {
            super(null);
        }

        @Override
        public List<Detection> findForExport(DetectionFilter filter, int limit) {
            return rows;
        }
    }
}
