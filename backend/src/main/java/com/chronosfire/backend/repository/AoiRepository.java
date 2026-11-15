package com.chronosfire.backend.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AoiRepository {

    private final JdbcTemplate jdbc;

    public AoiRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AoiRow> findAois() {
        return jdbc.query("""
                SELECT aoi_id, name, min_lat, min_lon, max_lat, max_lon,
                       observed_sensor, baseline_sensor, ref_from, ref_to, status, notes
                FROM validation_aoi
                ORDER BY aoi_id
                """, (rs, rowNum) -> new AoiRow(
                rs.getString("aoi_id"),
                rs.getString("name"),
                rs.getDouble("min_lat"),
                rs.getDouble("min_lon"),
                rs.getDouble("max_lat"),
                rs.getDouble("max_lon"),
                rs.getString("observed_sensor"),
                rs.getString("baseline_sensor"),
                rs.getInt("ref_from"),
                rs.getInt("ref_to"),
                rs.getString("status"),
                rs.getString("notes")));
    }

    public List<WindowRow> findWindows() {
        return jdbc.query("""
                SELECT aoi_id, isoyear, week_from, week_to, expected_peak
                FROM validation_aoi_window
                ORDER BY aoi_id, isoyear, week_from
                """, (rs, rowNum) -> new WindowRow(
                rs.getString("aoi_id"),
                rs.getInt("isoyear"),
                rs.getInt("week_from"),
                rs.getInt("week_to"),
                rs.getObject("expected_peak", Integer.class)));
    }

    public record AoiRow(
            String aoiId,
            String name,
            double minLat,
            double minLon,
            double maxLat,
            double maxLon,
            String observedSensor,
            String baselineSensor,
            int refFrom,
            int refTo,
            String status,
            String notes
    ) {
    }

    public record WindowRow(
            String aoiId,
            int isoyear,
            int weekFrom,
            int weekTo,
            Integer expectedPeak
    ) {
    }
}
