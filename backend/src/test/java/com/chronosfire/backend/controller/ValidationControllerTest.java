package com.chronosfire.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronosfire.backend.dto.ValidationEvent;
import com.chronosfire.backend.service.ValidationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ValidationController.class)
@Import(ValidationControllerTest.TestConfig.class)
class ValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubValidationService validationService;

    @Test
    void returnsValidationEvents() throws Exception {
        validationService.events = List.of(new ValidationEvent(
                "delta_2020",
                "Delta del Parana",
                2020,
                40,
                66,
                10.0,
                12,
                6.6,
                "high",
                "pass"));

        mockMvc.perform(get("/api/validation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].aoiId").value("delta_2020"))
                .andExpect(jsonPath("$[0].anomaly").value(6.6))
                .andExpect(jsonPath("$[0].verdict").value("pass"));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubValidationService validationService() {
            return new StubValidationService();
        }
    }

    static class StubValidationService extends ValidationService {

        private List<ValidationEvent> events = List.of();

        StubValidationService() {
            super(null, null);
        }

        @Override
        public List<ValidationEvent> getValidation() {
            return events;
        }
    }
}
