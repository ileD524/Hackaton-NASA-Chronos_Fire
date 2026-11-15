package com.chronosfire.backend.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de la capa de riesgo materializada (fases 0 y 1).
 *
 * <p>Solo lectura. {@code risk_cell_week} se construye con
 * {@code backend/db/rebuild_risk.sql}.</p>
 */
@Repository
public class RiskRepository {

    private final JdbcTemplate jdbc;

    public RiskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Riesgo materializado de un conjunto de celdas para una semana ISO y era.
     * Las celdas sin fila no aparecen en el mapa.
     */
    public Map<String, CellWeekRisk> byCellsWeek(Collection<String> gridIds, int week, String era) {
        if (gridIds == null || gridIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("""
                SELECT grid_id, ref_years, fire_years, fire_days,
                       p_fire, p_lo, p_hi, det_p50, det_p90, reliability
                FROM risk_cell_week
                WHERE era = ?
                  AND week_of_year = ?
                  AND grid_id = ANY(string_to_array(?, ','))
                """, rs -> {
            Map<String, CellWeekRisk> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("grid_id"), new CellWeekRisk(
                        rs.getInt("ref_years"),
                        rs.getInt("fire_years"),
                        rs.getInt("fire_days"),
                        rs.getDouble("p_fire"),
                        rs.getDouble("p_lo"),
                        rs.getDouble("p_hi"),
                        nullableDouble(rs, "det_p50"),
                        nullableDouble(rs, "det_p90"),
                        rs.getString("reliability")));
            }
            return result;
        }, era, week, csv(gridIds));
    }

    /**
     * Detecciones semanales historicas por celda para la semana ISO y era.
     * Solo incluye anios con actividad: los anios ausentes tuvieron 0
     * detecciones y se completan con cero al calcular el percentil.
     */
    public Map<String, List<Integer>> weeklyDetections(Collection<String> gridIds, int week, String era) {
        if (gridIds == null || gridIds.isEmpty()) {
            return Map.of();
        }
        int fromYear = "MODIS".equals(era) ? 2003 : 2013;
        int toYear = "MODIS".equals(era) ? 2011 : 2025;
        String sensor = "MODIS".equals(era) ? "MODIS" : "VIIRS_SNPP";
        return jdbc.query("""
                SELECT grid_id, n_detections
                FROM grid_weekly_sensor
                WHERE sensor = ?
                  AND week_of_year = ?
                  AND isoyear BETWEEN ? AND ?
                  AND grid_id = ANY(string_to_array(?, ','))
                ORDER BY grid_id, isoyear
                """, rs -> {
            Map<String, List<Integer>> result = new LinkedHashMap<>();
            while (rs.next()) {
                result.computeIfAbsent(rs.getString("grid_id"), key -> new java.util.ArrayList<>())
                        .add(rs.getInt("n_detections"));
            }
            return result;
        }, sensor, week, fromYear, toYear, csv(gridIds));
    }

    /**
     * Los grid_id de 0,05 grados son {@code 0.05:lat:lon}: no contienen comas,
     * asi que se pueden pasar como lista separada por comas sin escapar.
     */
    private String csv(Collection<String> gridIds) {
        return String.join(",", gridIds);
    }

    private Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        float value = rs.getFloat(column);
        return rs.wasNull() ? null : (double) value;
    }

    public record CellWeekRisk(
            int refYears,
            int fireYears,
            int fireDays,
            double pFire,
            double pLo,
            double pHi,
            Double detP50,
            Double detP90,
            String reliability
    ) {
    }
}
