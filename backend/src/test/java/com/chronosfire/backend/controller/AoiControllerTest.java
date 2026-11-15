package com.chronosfire.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronosfire.backend.dto.Aoi;
import com.chronosfire.backend.dto.AoiWindow;
import com.chronosfire.backend.service.AoiService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AoiController.class)
@Import(AoiControllerTest.TestConfig.class)
class AoiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubAoiService aoiService;

    @Test
    void returnsAois() throws Exception {
        aoiService.aois = List.of(new Aoi(
                "delta_2020",
                "Delta del Parana",
                -34,
                -61,
                -32.5,
                -59.5,
                "VIIRS_SNPP",
                "VIIRS_SNPP",
                2013,
                2025,
                "confirmed",
                "demo",
                List.of(new AoiWindow(2020, 36, 42, 40))));

        mockMvc.perform(get("/api/aois"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].aoiId").value("delta_2020"))
                .andExpect(jsonPath("$[0].windows[0].expectedPeak").value(40));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubAoiService aoiService() {
            return new StubAoiService();
        }
    }

    static class StubAoiService extends AoiService {

        private List<Aoi> aois = List.of();

        StubAoiService() {
            super(null);
        }

        @Override
        public List<Aoi> getAois() {
            return aois;
        }
    }
}
