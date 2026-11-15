package com.chronosfire.backend.repository;

import com.chronosfire.backend.dto.MetaResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MetaRepository {

    private final JdbcTemplate jdbc;

    public MetaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MetaResponse.SensorCoverage> sensorCoverage() {
        return jdbc.query("""
                SELECT sensor, source, count(*) AS rows, min(acq_date) AS from_date, max(acq_date) AS to_date
                FROM detections
                GROUP BY sensor, source
                ORDER BY sensor, source
                """, (rs, rowNum) -> new MetaResponse.SensorCoverage(
                rs.getString("sensor"),
                rs.getString("source"),
                rs.getLong("rows"),
                rs.getObject("from_date", LocalDate.class),
                rs.getObject("to_date", LocalDate.class)));
    }

    public MetaResponse.DateRange dateRange() {
        return jdbc.query("SELECT min(acq_date) AS from_date, max(acq_date) AS to_date FROM detections", rs -> {
            if (!rs.next()) {
                return new MetaResponse.DateRange(null, null);
            }
            return new MetaResponse.DateRange(
                    rs.getObject("from_date", LocalDate.class),
                    rs.getObject("to_date", LocalDate.class));
        });
    }

    public Instant lastDetectionAt() {
        return jdbc.query("""
                SELECT acquired_at
                FROM detections
                ORDER BY acq_date DESC, acquired_at DESC
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            OffsetDateTime value = rs.getObject("acquired_at", OffsetDateTime.class);
            return value == null ? null : value.toInstant();
        });
    }

    public MetaResponse.Counts counts() {
        long detections = tableCount("public.detections");
        long gridDaily = tableCount("public.grid_daily");
        long gridWeekly = tableCount("public.grid_weekly");
        long gridWeeklySensor = tableCount("public.grid_weekly_sensor");
        long calendarWeekly = tableCount("public.calendar_weekly");
        long industrialCells = tableCount("public.industrial_cells");
        long coverageDaily = tableCount("public.coverage_daily");
        long validationAois = tableCount("public.validation_aoi");
        return new MetaResponse.Counts(
                detections,
                gridDaily,
                gridWeekly,
                gridWeeklySensor,
                calendarWeekly,
                industrialCells,
                coverageDaily,
                validationAois);
    }

    /**
     * Version publicada de la capa de riesgo. {@code null} si las tablas de
     * riesgo no existen (por ejemplo, en un dump de demo sin la fase 0).
     */
    public String riskModelVersion() {
        if (!tableExists("public.risk_cell_week")) {
            return null;
        }
        List<String> versions = jdbc.query("""
                SELECT model_version
                FROM risk_cell_week
                WHERE era = 'SNPP'
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("model_version"));
        return versions.isEmpty() ? null : versions.get(0);
    }

    /** Filas de calibracion por ventana de entrenamiento, si el backtest corrio. */
    public List<MetaResponse.RiskBacktest> riskBacktest() {
        if (!tableExists("public.risk_backtest")) {
            return List.of();
        }
        return jdbc.query("""
                SELECT model_version, holdout_year, brier, bss, auc, n
                FROM risk_backtest
                ORDER BY holdout_year DESC, model_version
                """, (rs, rowNum) -> new MetaResponse.RiskBacktest(
                rs.getString("model_version"),
                (Integer) rs.getObject("holdout_year", Integer.class),
                nullableFloat(rs, "brier"),
                nullableFloat(rs, "bss"),
                nullableFloat(rs, "auc"),
                (Long) rs.getObject("n", Long.class)));
    }

    private Double nullableFloat(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        float value = rs.getFloat(column);
        return rs.wasNull() ? null : (double) value;
    }

    private long tableCount(String qualifiedTable) {
        if (!tableExists(qualifiedTable)) {
            return 0L;
        }
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + qualifiedTable, Long.class);
        return count == null ? 0L : count;
    }

    private boolean tableExists(String qualifiedTable) {
        Boolean exists = jdbc.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL",
                Boolean.class,
                qualifiedTable);
        return Boolean.TRUE.equals(exists);
    }
}
