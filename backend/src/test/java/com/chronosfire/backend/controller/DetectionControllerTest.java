package com.chronosfire.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronosfire.backend.dto.Detection;
import com.chronosfire.backend.dto.DetectionPage;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection.GeoJsonFeature;
import com.chronosfire.backend.service.DetectionService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DetectionController.class)
@Import(DetectionControllerTest.TestConfig.class)
class DetectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubDetectionService detectionService;

    @Test
    void returnsPaginatedDetections() throws Exception {
        detectionService.page = new DetectionPage(
                List.of(sampleDetection()),
                1,
                1000,
                0);

        mockMvc.perform(get("/api/detections")
                        .param("from", "2020-09-01")
                        .param("to", "2020-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].sensor").value("MODIS"))
                .andExpect(jsonPath("$.items[0].industrialMask").value(false));
    }

    @Test
    void returnsGeoJson() throws Exception {
        detectionService.geoJson = GeoJsonFeatureCollection.of(List.of(
                GeoJsonFeature.point(-60.3, -33.25, Map.of("sensor", "MODIS"))));

        mockMvc.perform(get("/api/detections/geojson")
                        .param("from", "2020-09-01")
                        .param("to", "2020-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].properties.sensor").value("MODIS"));
    }

    @Test
    void exportsCsv() throws Exception {
        detectionService.csv = "\uFEFFsensor,source\nMODIS,SP\n";

        mockMvc.perform(get("/api/detections/export.csv")
                        .param("from", "2020-09-01")
                        .param("to", "2020-09-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("sensor,source")));
    }

    private Detection sampleDetection() {
        return new Detection(
                "MODIS",
                "SP",
                Instant.parse("2020-09-04T12:00:00Z"),
                LocalDate.parse("2020-09-04"),
                -33.25,
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

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubDetectionService detectionService() {
            return new StubDetectionService();
        }
    }

    static class StubDetectionService extends DetectionService {

        private DetectionPage page;
        private GeoJsonFeatureCollection geoJson;
        private String csv;

        StubDetectionService() {
            super(null);
        }

        @Override
        public DetectionPage getPage(
                String bbox,
                LocalDate from,
                LocalDate to,
                String sensor,
                List<String> confidence,
                Integer type,
                String source,
                Boolean vegetation,
                int limit,
                int offset) {
            return page;
        }

        @Override
        public GeoJsonFeatureCollection getGeoJson(
                String bbox,
                LocalDate from,
                LocalDate to,
                String sensor,
                List<String> confidence,
                Integer type,
                String source,
                Boolean vegetation,
                int limit) {
            return geoJson;
        }

        @Override
        public String exportCsv(
                String bbox,
                LocalDate from,
                LocalDate to,
                String sensor,
                List<String> confidence,
                Integer type,
                String source,
                Boolean vegetation,
                int limit) {
            return csv;
        }
    }
}
