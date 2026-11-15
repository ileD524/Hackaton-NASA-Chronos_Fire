package com.chronosfire.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.CsvExport;
import com.chronosfire.backend.dto.HeatmapCell;
import com.chronosfire.backend.dto.MonthlyCell;
import com.chronosfire.backend.dto.TrendRow;
import com.chronosfire.backend.dto.WeeklyResponse;
import com.chronosfire.backend.service.CalendarService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CalendarController.class)
@Import(CalendarControllerTest.TestConfig.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubCalendarService calendarService;

    @Test
    void returnsWeeklyCalendar() throws Exception {
        calendarService.setResponse(new WeeklyResponse(
                new Bbox(-61, -34, -59.5, -32.5),
                2020,
                40,
                66,
                10.0,
                6.6,
                12,
                "2013-2025",
                "VIIRS_SNPP",
                "VIIRS_SNPP",
                331,
                "high",
                false,
                "observed",
                null,
                Map.of("MODIS", 20, "VIIRS_SNPP", 66),
                Map.of("MODIS", 120, "VIIRS_SNPP", 2355),
                Map.of("MODIS", 500.0, "VIIRS_SNPP", 1000.0),
                0.593));

        mockMvc.perform(get("/api/calendar/weekly")
                        .param("bbox", "-61,-34,-59.5,-32.5")
                        .param("year", "2020")
                        .param("week", "40")
                        .param("sensor", "AUTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observedFireCells").value(66))
                .andExpect(jsonPath("$.baselineFireCells").value(10.0))
                .andExpect(jsonPath("$.anomaly").value(6.6))
                .andExpect(jsonPath("$.referenceYears").value(12))
                .andExpect(jsonPath("$.observedSensor").value("VIIRS_SNPP"))
                .andExpect(jsonPath("$.confidence").value("high"))
                .andExpect(jsonPath("$.fireCellsBySensor.VIIRS_SNPP").value(66));
    }

    @Test
    void returnsMonthlyCalendarAndForwardsSensor() throws Exception {
        calendarService.setMonthlyResponse(List.of(new MonthlyCell(
                2020,
                8,
                66,
                78,
                1242,
                Map.of("VIIRS_SNPP", 1242),
                1180,
                Map.of("VIIRS_SNPP", 1180),
                false,
                "observed",
                10.0,
                6.6,
                12,
                "high")));

        mockMvc.perform(get("/api/calendar/monthly")
                        .param("bbox", "-61,-34,-59.5,-32.5")
                        .param("from", "2020-09-01")
                        .param("to", "2020-09-30")
                        .param("sensor", "VIIRS_SNPP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fireCells").value(66))
                .andExpect(jsonPath("$[0].fireDays").value(78))
                .andExpect(jsonPath("$[0].nDetections").value(1242))
                .andExpect(jsonPath("$[0].detectionsBySensor.VIIRS_SNPP").value(1242))
                .andExpect(jsonPath("$[0].hotspots").value(1180))
                .andExpect(jsonPath("$[0].hotspotsBySensor.VIIRS_SNPP").value(1180))
                .andExpect(jsonPath("$[0].baselineFireCells").value(10.0))
                .andExpect(jsonPath("$[0].anomaly").value(6.6))
                .andExpect(jsonPath("$[0].referenceYears").value(12))
                .andExpect(jsonPath("$[0].confidence").value("high"));

        assertEquals("VIIRS_SNPP", calendarService.getMonthlySensor());
    }

    @Test
    void returnsHeatmapWithHotspotBreakdown() throws Exception {
        calendarService.setHeatmapResponse(List.of(new HeatmapCell(
                "0.05:-660:-1200",
                -33.0,
                -60.0,
                1,
                4,
                10,
                7,
                Map.of("MODIS", 2, "VIIRS_SNPP", 5),
                null,
                "observed",
                0.41,
                0.18,
                0.68,
                13,
                "high")));

        mockMvc.perform(get("/api/calendar/heatmap")
                        .param("bbox", "-61,-34,-59.5,-32.5")
                        .param("from", "2020-09-01")
                        .param("to", "2020-09-30")
                        .param("sensor", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hotspots").value(7))
                .andExpect(jsonPath("$[0].hotspotsBySensor.MODIS").value(2))
                .andExpect(jsonPath("$[0].hotspotsBySensor.VIIRS_SNPP").value(5))
                .andExpect(jsonPath("$[0].nDetections").value(10))
                .andExpect(jsonPath("$[0].fireProbability").value(0.41))
                .andExpect(jsonPath("$[0].probabilityLow").value(0.18))
                .andExpect(jsonPath("$[0].probabilityRefYears").value(13))
                .andExpect(jsonPath("$[0].probabilityReliability").value("high"));
    }

    @Test
    void returnsTrendWithBaselineAndAnomaly() throws Exception {
        calendarService.setTrendResponse(List.of(new TrendRow(
                2020,
                189,
                62.0,
                3.05,
                12,
                "high",
                false,
                "VIIRS_SNPP",
                "VIIRS_SNPP")));

        mockMvc.perform(get("/api/calendar/trend")
                        .param("bbox", "-61,-34,-59.5,-32.5")
                        .param("from", "2019-01-01")
                        .param("to", "2020-12-31")
                        .param("sensor", "AUTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].year").value(2020))
                .andExpect(jsonPath("$[0].fireCells").value(189))
                .andExpect(jsonPath("$[0].baselineFireCells").value(62.0))
                .andExpect(jsonPath("$[0].anomaly").value(3.05))
                .andExpect(jsonPath("$[0].referenceYears").value(12))
                .andExpect(jsonPath("$[0].confidence").value("high"))
                .andExpect(jsonPath("$[0].observedSensor").value("VIIRS_SNPP"));
    }

    @Test
    void exportsCalendarCsvWithTruncationHeaders() throws Exception {
        calendarService.setExportResponse(new CsvExport(
                "\uFEFFisoyear,week_of_year\n2020,40\n",
                10,
                10,
                true));

        mockMvc.perform(get("/api/calendar/export.csv")
                        .param("bbox", "-61,-34,-59.5,-32.5")
                        .param("from", "2020-09-01")
                        .param("to", "2020-09-30")
                        .param("sensor", "ALL")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Row-Limit", "10"))
                .andExpect(header().string("X-Returned-Rows", "10"))
                .andExpect(header().string("X-Truncated", "true"));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubCalendarService calendarService() {
            return new StubCalendarService();
        }
    }

    static class StubCalendarService extends CalendarService {

        private WeeklyResponse response;
        private List<MonthlyCell> monthlyResponse = List.of();
        private String monthlySensor;
        private List<HeatmapCell> heatmapResponse = List.of();
        private List<TrendRow> trendResponse = List.of();
        private CsvExport exportResponse;

        StubCalendarService() {
            super(null);
        }

        void setResponse(WeeklyResponse response) {
            this.response = response;
        }

        void setMonthlyResponse(List<MonthlyCell> monthlyResponse) {
            this.monthlyResponse = monthlyResponse;
        }

        void setHeatmapResponse(List<HeatmapCell> heatmapResponse) {
            this.heatmapResponse = heatmapResponse;
        }

        void setTrendResponse(List<TrendRow> trendResponse) {
            this.trendResponse = trendResponse;
        }

        void setExportResponse(CsvExport exportResponse) {
            this.exportResponse = exportResponse;
        }

        String getMonthlySensor() {
            return monthlySensor;
        }

        @Override
        public WeeklyResponse getWeekly(String bbox, int year, int week, String sensor) {
            return response;
        }

        @Override
        public List<MonthlyCell> getMonthly(
                String bbox,
                LocalDate from,
                LocalDate to,
                String sensor) {
            monthlySensor = sensor;
            return monthlyResponse;
        }

        @Override
        public List<HeatmapCell> getHeatmap(
                String bbox,
                LocalDate from,
                LocalDate to,
                String sensor,
                int limit) {
            return heatmapResponse;
        }

        @Override
        public List<TrendRow> getTrend(
                String bbox,
                LocalDate from,
                LocalDate to,
                String sensor) {
            return trendResponse;
        }

        @Override
        public CsvExport exportCsv(
                String bbox,
                LocalDate from,
                LocalDate to,
                String sensor,
                int limit) {
            return exportResponse;
        }
    }
}
