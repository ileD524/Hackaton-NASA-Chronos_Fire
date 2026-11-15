package com.chronosfire.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronosfire.backend.dto.MetaResponse;
import com.chronosfire.backend.service.MetaService;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MetaController.class)
@Import(MetaControllerTest.TestConfig.class)
class MetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubMetaService metaService;

    @Test
    void returnsMetadata() throws Exception {
        metaService.setResponse(new MetaResponse(
                "v1",
                Instant.parse("2026-11-14T12:00:00Z"),
                Instant.parse("2026-11-14T23:59:00Z"),
                "chronos_fire",
                0.05,
                List.of(new MetaResponse.SensorCoverage(
                        "MODIS",
                        "SP",
                        1726328L,
                        LocalDate.parse("2000-11-01"),
                        LocalDate.parse("2026-08-31"))),
                List.of("SP", "NRT"),
                new MetaResponse.DateRange(
                        LocalDate.parse("2000-11-01"),
                        LocalDate.parse("2026-11-14")),
                new MetaResponse.Counts(
                        7082474L,
                        2103053L,
                        1575089L,
                        2265477L,
                        3719116L,
                        885L,
                        0L,
                        5L),
                Map.of("fire_day", "Celda de 0,05 grados con actividad"),
                List.of("Una semana sin filas no confirma ausencia de fuego."),
                "NASA FIRMS",
                new MetaResponse.ProvisionalWindow(
                        LocalDate.parse("2026-08-01"),
                        LocalDate.parse("2026-11-14"),
                        "NRT"),
                new MetaResponse.RiskMethodology(
                        "risk-v1-low",
                        "low",
                        2025,
                        0.01148,
                        0.0286,
                        0.74,
                        3719116L,
                        0.0236,
                        0.05,
                        "BSS 0.0286 < 0.05 en el holdout 2025: se publica como risk-v1-low.")));

        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.gridResolutionDeg").value(0.05))
                .andExpect(jsonPath("$.sensors[0].sensor").value("MODIS"))
                .andExpect(jsonPath("$.counts.detections").value(7082474))
                .andExpect(jsonPath("$.provisional.reason").value("NRT"))
                .andExpect(jsonPath("$.risk.modelVersion").value("risk-v1-low"))
                .andExpect(jsonPath("$.risk.calibrationStatus").value("low"))
                .andExpect(jsonPath("$.risk.bss").value(0.0286))
                .andExpect(jsonPath("$.risk.holdoutYear").value(2025));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubMetaService metaService() {
            return new StubMetaService();
        }
    }

    static class StubMetaService extends MetaService {

        private MetaResponse response;

        StubMetaService() {
            super(null);
        }

        void setResponse(MetaResponse response) {
            this.response = response;
        }

        @Override
        public MetaResponse getMeta() {
            return response;
        }
    }
}
