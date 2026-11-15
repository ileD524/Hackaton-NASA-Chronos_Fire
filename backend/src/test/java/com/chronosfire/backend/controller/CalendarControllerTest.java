package com.chronosfire.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.WeeklyResponse;
import com.chronosfire.backend.service.CalendarService;
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

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubCalendarService calendarService() {
            return new StubCalendarService();
        }
    }

    static class StubCalendarService extends CalendarService {

        private WeeklyResponse response;

        StubCalendarService() {
            super(null);
        }

        void setResponse(WeeklyResponse response) {
            this.response = response;
        }

        @Override
        public WeeklyResponse getWeekly(String bbox, int year, int week, String sensor) {
            return response;
        }
    }
}
