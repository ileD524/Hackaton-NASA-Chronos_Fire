package com.chronosfire.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronosfire.backend.dto.HealthResponse;
import com.chronosfire.backend.service.HealthService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import(HealthControllerTest.TestConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubHealthService healthService;

    @Test
    void returnsOkWhenDatabaseIsAvailable() throws Exception {
        healthService.setResponse(new HealthResponse(
                "ok",
                true,
                "PostgreSQL 17.5",
                3L,
                Instant.parse("2026-11-14T23:59:00Z"),
                7082474L));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").value(true))
                .andExpect(jsonPath("$.latencyMs").value(3))
                .andExpect(jsonPath("$.detectionsApprox").value(7082474));
    }

    @Test
    void returnsServiceUnavailableWhenDatabaseFails() throws Exception {
        healthService.setResponse(new HealthResponse(
                "degraded",
                false,
                null,
                12L,
                null,
                0L));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.database").value(false));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubHealthService healthService() {
            return new StubHealthService();
        }
    }

    static class StubHealthService extends HealthService {

        private HealthResponse response;

        StubHealthService() {
            super(null);
        }

        void setResponse(HealthResponse response) {
            this.response = response;
        }

        @Override
        public HealthResponse getHealth() {
            return response;
        }
    }
}
