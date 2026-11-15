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

    private long tableCount(String qualifiedTable) {
        Boolean exists = jdbc.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL",
                Boolean.class,
                qualifiedTable);
        if (!Boolean.TRUE.equals(exists)) {
            return 0L;
        }
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + qualifiedTable, Long.class);
        return count == null ? 0L : count;
    }
}
