package com.chronosfire.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronosfire.backend.dto.Alert;
import com.chronosfire.backend.dto.AlertResponse;
import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection;
import com.chronosfire.backend.dto.GeoJsonFeatureCollection.GeoJsonFeature;
import com.chronosfire.backend.service.AlertService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AlertController.class)
@Import(AlertControllerTest.TestConfig.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubAlertService alertService;

    @Test
    void returnsAlerts() throws Exception {
        alertService.response = new AlertResponse(
                Instant.parse("2026-11-15T00:00:00Z"),
                Instant.parse("2026-11-15T00:00:00Z"),
                Instant.parse("2026-11-14T11:57:00Z"),
                7,
                new Bbox(-61, -34, -59.5, -32.5),
                1,
                true,
                List.of(new Alert(
                        -33.0,
                        -60.0,
                        Instant.parse("2026-11-14T11:57:00Z"),
                        14,
                        52.3,
                        17.5,
                        List.of("VIIRS_SNPP"),
                        "high",
                        1.8,
                        "watch",
                        false)));

        mockMvc.perform(get("/api/alerts")
                        .param("bbox", "-61,-34,-59.5,-32.5")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.provisional").value(true))
                .andExpect(jsonPath("$.alerts[0].level").value("watch"))
                .andExpect(jsonPath("$.alerts[0].nDetections").value(14));
    }

    @Test
    void returnsGeoJsonAlerts() throws Exception {
        alertService.geoJson = GeoJsonFeatureCollection.of(List.of(
                GeoJsonFeature.point(-60.0, -33.0, Map.of("level", "watch"))));

        mockMvc.perform(get("/api/alerts/geojson"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].properties.level").value("watch"));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubAlertService alertService() {
            return new StubAlertService();
        }
    }

    static class StubAlertService extends AlertService {

        private AlertResponse response;
        private GeoJsonFeatureCollection geoJson;

        StubAlertService() {
            super(null, null);
        }

        @Override
        public AlertResponse getAlerts(String bbox, int days, int limit) {
            return response;
        }

        @Override
        public GeoJsonFeatureCollection getGeoJson(String bbox, int days, int limit) {
            return geoJson;
        }
    }
}
