package com.chronosfire.backend.service;

import com.chronosfire.backend.repository.RiskRepository;
import com.chronosfire.backend.repository.RiskRepository.CellWeekRisk;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Orquesta la capa de riesgo para las respuestas de la API (fase 2).
 *
 * <p>La tabla {@code risk_cell_week} es estatica hasta el proximo rebuild, por
 * eso se cachea. El backend sigue siendo de solo lectura.</p>
 */
@Service
public class RiskService {

    private final RiskRepository repository;

    public RiskService(RiskRepository repository) {
        this.repository = repository;
    }

    /**
     * Probabilidad historica por celda para una semana ISO y era.
     *
     * @param observedByCell detecciones observadas en la ventana, por grid_id
     * @param week           semana ISO de la consulta
     * @param era            {@code MODIS} o {@code SNPP}
     * @param days           ventana de la alerta; el percentil solo se calcula
     *                       con 7 dias (la unidad historica es semanal)
     */
    @Cacheable(value = "risk",
            key = "'a|' + #week + '|' + #era + '|' + #days + '|' + #observedByCell")
    public Map<String, Probability> alertProbabilities(
            Map<String, Integer> observedByCell,
            int week,
            String era,
            int days) {
        if (observedByCell == null || observedByCell.isEmpty()) {
            return Map.of();
        }

        List<String> gridIds = List.copyOf(observedByCell.keySet());
        Map<String, CellWeekRisk> risks = repository.byCellsWeek(gridIds, week, era);
        Map<String, List<Integer>> historical = days == 7
                ? repository.weeklyDetections(gridIds, week, era)
                : Map.of();

        Map<String, Probability> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : observedByCell.entrySet()) {
            String gridId = entry.getKey();
            CellWeekRisk risk = risks.get(gridId);
            if (risk == null || "insufficient".equals(risk.reliability())) {
                result.put(gridId, Probability.insufficient(risk == null ? null : risk.refYears()));
                continue;
            }

            Double percentile = days == 7
                    ? RiskMath.activityPercentile(
                            historical.getOrDefault(gridId, List.of()),
                            risk.refYears(),
                            entry.getValue())
                    : null;

            result.put(gridId, new Probability(
                    risk.pFire(),
                    risk.pLo(),
                    risk.pHi(),
                    risk.refYears(),
                    risk.reliability(),
                    percentile));
        }
        return result;
    }

    /**
     * Campos de probabilidad que viajan en cada alerta o celda del mapa.
     *
     * <p>Si la confiabilidad es {@code insufficient}, {@code p} y el intervalo
     * van en {@code null}: el contrato no permite mostrar una probabilidad sin
     * respaldo historico.</p>
     */
    public record Probability(
            Double fireProbability,
            Double probabilityLow,
            Double probabilityHigh,
            Integer probabilityRefYears,
            String probabilityReliability,
            Double activityPercentile
    ) {

        public static Probability unknown() {
            return new Probability(null, null, null, null, "insufficient", null);
        }

        public static Probability insufficient(Integer refYears) {
            return new Probability(null, null, null, refYears, "insufficient", null);
        }
    }
}
