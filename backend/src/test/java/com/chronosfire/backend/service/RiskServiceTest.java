package com.chronosfire.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chronosfire.backend.repository.RiskRepository;
import com.chronosfire.backend.repository.RiskRepository.CellWeekRisk;
import com.chronosfire.backend.service.RiskService.Probability;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskServiceTest {

    @Test
    void mapsMaterializedRiskAndComputesPercentile() {
        StubRiskRepository repository = new StubRiskRepository();
        repository.risks = Map.of("0.05:-683:-1180",
                new CellWeekRisk(13, 4, 4, 0.41, 0.18, 0.68, 6.0, 18.0, "high"));
        repository.history = Map.of("0.05:-683:-1180",
                List.of(1, 1, 2, 2, 3, 3, 4, 5, 6, 8, 10, 12, 14));

        Probability probability = new RiskService(repository)
                .alertProbabilities(Map.of("0.05:-683:-1180", 14), 40, "SNPP", 7)
                .get("0.05:-683:-1180");

        assertEquals(0.41, probability.fireProbability(), 0.0001);
        assertEquals(0.18, probability.probabilityLow(), 0.0001);
        assertEquals(0.68, probability.probabilityHigh(), 0.0001);
        assertEquals(13, probability.probabilityRefYears());
        assertEquals("high", probability.probabilityReliability());
        assertEquals(1.0, probability.activityPercentile(), 0.0001);
        assertEquals("SNPP", repository.lastEra);
        assertEquals(40, repository.lastWeek);
    }

    @Test
    void percentileIsNullWhenWindowIsNotSevenDays() {
        StubRiskRepository repository = new StubRiskRepository();
        repository.risks = Map.of("gc", new CellWeekRisk(13, 4, 4, 0.41, 0.18, 0.68, 6.0, 18.0, "high"));
        repository.history = Map.of("gc", List.of(1, 2, 3));

        Probability probability = new RiskService(repository)
                .alertProbabilities(Map.of("gc", 5), 40, "SNPP", 3)
                .get("gc");

        assertEquals(0.41, probability.fireProbability(), 0.0001);
        assertNull(probability.activityPercentile());
        assertTrue(repository.weeklyDetectionsCalls == 0,
                "No debe consultar el historico semanal si days != 7");
    }

    @Test
    void insufficientReliabilityHidesProbability() {
        StubRiskRepository repository = new StubRiskRepository();
        repository.risks = Map.of("gc", new CellWeekRisk(2, 1, 1, 0.9, 0.5, 0.99, 1.0, 2.0, "insufficient"));

        Probability probability = new RiskService(repository)
                .alertProbabilities(Map.of("gc", 20), 53, "SNPP", 7)
                .get("gc");

        assertNull(probability.fireProbability());
        assertNull(probability.probabilityLow());
        assertNull(probability.probabilityHigh());
        assertEquals(2, probability.probabilityRefYears());
        assertEquals("insufficient", probability.probabilityReliability());
        assertNull(probability.activityPercentile());
    }

    @Test
    void unknownCellIsInsufficient() {
        StubRiskRepository repository = new StubRiskRepository();

        Probability probability = new RiskService(repository)
                .alertProbabilities(Map.of("fuera-de-tabla", 3), 40, "SNPP", 7)
                .get("fuera-de-tabla");

        assertEquals(Probability.unknown(), probability);
        assertNull(probability.fireProbability());
        assertNull(probability.probabilityRefYears());
    }

    @Test
    void emptyInputDoesNotHitTheDatabase() {
        StubRiskRepository repository = new StubRiskRepository();

        Map<String, Probability> probabilities = new RiskService(repository)
                .alertProbabilities(Map.of(), 40, "SNPP", 7);

        assertTrue(probabilities.isEmpty());
        assertTrue(repository.byCellsCalls == 0);
    }

    private static final class StubRiskRepository extends RiskRepository {

        private Map<String, CellWeekRisk> risks = Map.of();
        private Map<String, List<Integer>> history = new LinkedHashMap<>();
        private int byCellsCalls;
        private int weeklyDetectionsCalls;
        private String lastEra;
        private int lastWeek;

        private StubRiskRepository() {
            super(null);
        }

        @Override
        public Map<String, CellWeekRisk> byCellsWeek(Collection<String> gridIds, int week, String era) {
            byCellsCalls++;
            lastEra = era;
            lastWeek = week;
            return risks;
        }

        @Override
        public Map<String, List<Integer>> weeklyDetections(Collection<String> gridIds, int week, String era) {
            weeklyDetectionsCalls++;
            return history;
        }
    }
}
