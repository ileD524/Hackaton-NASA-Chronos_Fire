package com.chronosfire.backend.repository;

import com.chronosfire.backend.dto.Detection;
import com.chronosfire.backend.dto.DetectionPage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DetectionRepository {

    private static final String SELECT_COLUMNS = """
            SELECT sensor, source, acquired_at, acq_date, latitude, longitude,
                   confidence_raw, confidence_norm, confidence_class, fire_type,
                   is_vegetation_effective, industrial_mask,
                   frp_mw, frp_density_wm2, pixel_area_km2, quality_flags, product_version
            FROM v_detections_analysis
            """;

    private final JdbcTemplate jdbc;

    public DetectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DetectionPage find(DetectionFilter filter, int limit, int offset) {
        Where where = where(filter);
        String sql = SELECT_COLUMNS
                + where.sql()
                + " ORDER BY acquired_at DESC, sensor, latitude, longitude\n"
                + " LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(where.params());
        params.add(limit);
        params.add(offset);

        List<Detection> items = jdbc.query(sql, this::map, params.toArray());
        long total = count(filter);
        return new DetectionPage(items, total, limit, offset);
    }

    public List<Detection> findForExport(DetectionFilter filter, int limit) {
        Where where = where(filter);
        String sql = SELECT_COLUMNS
                + where.sql()
                + " ORDER BY acquired_at DESC, sensor, latitude, longitude\n"
                + " LIMIT ?";
        List<Object> params = new ArrayList<>(where.params());
        params.add(limit);
        return jdbc.query(sql, this::map, params.toArray());
    }

    public long count(DetectionFilter filter) {
        Where where = where(filter);
        Long value = jdbc.queryForObject(
                "SELECT count(*) FROM v_detections_analysis " + where.sql(),
                Long.class,
                where.params().toArray());
        return value == null ? 0L : value;
    }

    private Where where(DetectionFilter filter) {
        StringBuilder sql = new StringBuilder("""
                WHERE acq_date BETWEEN ? AND ?
                  AND latitude BETWEEN ? AND ?
                  AND longitude BETWEEN ? AND ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(filter.from()));
        params.add(java.sql.Date.valueOf(filter.to()));
        params.add(filter.bbox().minLat());
        params.add(filter.bbox().maxLat());
        params.add(filter.bbox().minLon());
        params.add(filter.bbox().maxLon());

        if (!"ALL".equals(filter.sensor())) {
            sql.append("  AND sensor = ?\n");
            params.add(filter.sensor());
        }
        if (filter.confidence() != null && !filter.confidence().isEmpty()) {
            sql.append("  AND confidence_class IN (");
            for (int i = 0; i < filter.confidence().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                params.add(filter.confidence().get(i));
            }
            sql.append(")\n");
        }
        if (filter.fireType() != null) {
            sql.append("  AND fire_type = ?\n");
            params.add(filter.fireType());
        }
        if (filter.source() != null) {
            sql.append("  AND source = ?\n");
            params.add(filter.source());
        }
        if (Boolean.TRUE.equals(filter.vegetation())) {
            sql.append("""
                      AND (
                          is_vegetation_effective IS TRUE
                          OR (is_vegetation_effective IS NULL
                              AND confidence_class IN ('nominal', 'high'))
                      )
                    """);
        } else if (Boolean.FALSE.equals(filter.vegetation())) {
            sql.append("  AND is_vegetation_effective IS FALSE\n");
        }
        return new Where(sql.toString(), params);
    }

    private Detection map(ResultSet rs, int rowNum) throws SQLException {
        return new Detection(
                rs.getString("sensor"),
                rs.getString("source"),
                toInstant(rs, "acquired_at"),
                rs.getObject("acq_date", LocalDate.class),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("confidence_raw"),
                rs.getObject("confidence_norm", Integer.class),
                rs.getString("confidence_class"),
                rs.getObject("fire_type", Integer.class),
                rs.getObject("is_vegetation_effective", Boolean.class),
                rs.getBoolean("industrial_mask"),
                toDouble(rs, "frp_mw"),
                toDouble(rs, "frp_density_wm2"),
                toDouble(rs, "pixel_area_km2"),
                rs.getString("quality_flags"),
                rs.getString("product_version"));
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private Double toDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private record Where(String sql, List<Object> params) {
    }
}
