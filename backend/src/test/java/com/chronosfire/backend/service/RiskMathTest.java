package com.chronosfire.backend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RiskMathTest {

    @Test
    void wilsonMatchesKnownCaseForThirtyPercentAndTenYears() {
        double[] interval = RiskMath.wilson(3, 10);

        assertArrayEquals(new double[]{0.1268, 0.5583}, interval, 0.001);
    }

    @Test
    void wilsonForThirteenYearsIsWideOnPurpose() {
        double[] interval = RiskMath.wilson(4, 13);

        assertEquals(0.1463, interval[0], 0.001);
        assertEquals(0.5352, interval[1], 0.001);
        assertTrue(interval[1] - interval[0] > 0.35, "El intervalo con n=13 debe ser ancho");
    }

    @Test
    void wilsonIsNullWithoutReferenceYears() {
        assertNull(RiskMath.wilson(0, 0));
    }

    @Test
    void priorKeepsZeroFireCellsAboveZero() {
        Double probability = RiskMath.posterior(0, 13, 0.1, 7.9);

        assertEquals(0.0048, probability, 0.0001);
        assertTrue(probability > 0, "k=0 no debe afirmar p=0 exacto");
    }

    @Test
    void posteriorIsMonotonicInFireYears() {
        double previous = -1;
        for (int k = 0; k <= 13; k++) {
            double probability = RiskMath.posterior(k, 13, 0.1, 7.9);
            assertTrue(probability > previous, "p debe crecer con k");
            previous = probability;
        }
    }

    @Test
    void posteriorWithAllYearsOnFireStaysBelowOne() {
        Double probability = RiskMath.posterior(13, 13, 0.1, 7.9);

        assertEquals(0.6238, probability, 0.0001);
        assertTrue(probability < 1, "El suavizado no debe afirmar certeza");
    }

    @Test
    void posteriorIsNullWithoutCoverage() {
        assertNull(RiskMath.posterior(0, 0, 0.1, 7.9));
    }

    @Test
    void reliabilityFollowsPlanThresholds() {
        assertEquals("high", RiskMath.reliability(13));
        assertEquals("high", RiskMath.reliability(10));
        assertEquals("low", RiskMath.reliability(9));
        assertEquals("low", RiskMath.reliability(5));
        assertEquals("insufficient", RiskMath.reliability(4));
        assertEquals("insufficient", RiskMath.reliability(0));
    }

    @Test
    void percentileCountsYearsWithoutActivityAsZero() {
        List<Integer> historical = List.of(10, 20, 30, 40, 50);

        // 3 de 5 anios con actividad + 8 anios sin fila (0 detecciones) = 11/13
        assertEquals(0.8462, RiskMath.activityPercentile(historical, 13, 30), 0.0001);
        assertEquals(1.0, RiskMath.activityPercentile(List.of(), 13, 0), 0.0001);
        assertEquals(0.6154, RiskMath.activityPercentile(historical, 13, 0), 0.0001);
        assertNull(RiskMath.activityPercentile(historical, 0, 5));
        assertNull(RiskMath.activityPercentile(historical, 13, null));
    }

    @Test
    void levelEscalatesByRecurrenceAlone() {
        assertEquals("warning", RiskMath.level(0.50, null, 0));
        assertEquals("warning", RiskMath.level(0.60, 0.10, 1));
        assertEquals("watch", RiskMath.level(0.25, null, 0));
        assertEquals("watch", RiskMath.level(0.30, 0.10, 1));
    }

    @Test
    void levelUsesPercentileOnlyWithHistoricalBaseOrVolume() {
        // Base historica alcanza con p >= 0,10
        assertEquals("warning", RiskMath.level(0.10, 0.90, 4));
        assertEquals("watch", RiskMath.level(0.10, 0.75, 1));
        // Sin base, el volumen del evento habilita el ascenso
        assertEquals("warning", RiskMath.level(0.0037, 1.0, 309));
        assertEquals("warning", RiskMath.level(0.0037, 1.0, 10));
        assertEquals("watch", RiskMath.level(0.0037, 0.80, 3));
        // Sin base ni volumen, el percentil alto no escala
        assertEquals("info", RiskMath.level(0.0037, 1.0, 2));
        assertEquals("watch", RiskMath.level(0.09, 0.90, 8));
    }

    @Test
    void levelFallsBackToVolumeWhenPercentileIsUnavailable() {
        assertEquals("warning", RiskMath.level(0.08, null, 12));
        assertEquals("watch", RiskMath.level(0.08, null, 4));
        assertEquals("info", RiskMath.level(0.08, null, 2));
    }

    @Test
    void insufficientReliabilityIsAlwaysInfo() {
        assertEquals("info", RiskMath.level(null, null, 309));
    }
}
