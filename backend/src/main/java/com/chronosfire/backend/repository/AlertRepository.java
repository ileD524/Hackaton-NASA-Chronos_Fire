package com.chronosfire.backend.repository;

import com.chronosfire.backend.dto.Bbox;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlertRepository {

    private final JdbcTemplate jdbc;

    public AlertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AlertRow> alerts(Bbox bbox, Instant since, int limit) {
        return jdbc.query("""
                SELECT '0.05:' || CAST(round(latitude / 0.05) AS BIGINT) || ':' ||
                       CAST(round(longitude / 0.05) AS BIGINT) AS grid_id,
                       round(latitude / 0.05) * 0.05  AS cell_lat,
                       round(longitude / 0.05) * 0.05 AS cell_lon,
                       max(acquired_at)               AS last_detection,
                       count(*)::int                  AS n_detections,
                       max(frp_mw)::float8             AS frp_max,
                       max(frp_density_wm2)::float8    AS frp_density_max,
                       array_agg(DISTINCT sensor)      AS sensors,
                       max(confidence_norm)::int       AS confidence_max
                FROM v_detections_analysis
                WHERE source = 'NRT'
                  AND NOT industrial_mask
                  AND acquired_at >= ?
                  AND latitude BETWEEN ? AND ?
                  AND longitude BETWEEN ? AND ?
                  AND confidence_class IN ('nominal', 'high')
                  AND (is_vegetation_effective IS NULL OR is_vegetation_effective)
                GROUP BY 1, 2, 3
                ORDER BY n_detections DESC, last_detection DESC
                LIMIT ?
                """, (rs, rowNum) -> new AlertRow(
                rs.getString("grid_id"),
                round(rs.getDouble("cell_lat"), 4),
                round(rs.getDouble("cell_lon"), 4),
                toInstant(rs.getObject("last_detection", OffsetDateTime.class)),
                rs.getInt("n_detections"),
                round((Double) rs.getObject("frp_max"), 3),
                round((Double) rs.getObject("frp_density_max"), 3),
                toList(rs.getArray("sensors")),
                rs.getInt("confidence_max")),
                Timestamp.from(since),
                bbox.minLat(), bbox.maxLat(),
                bbox.minLon(), bbox.maxLon(),
                limit);
    }

    public Instant latestNrt() {
        return jdbc.query("SELECT max(acquired_at) AS latest FROM detections WHERE source = 'NRT'", rs -> {
            if (!rs.next()) {
                return null;
            }
            return toInstant(rs.getObject("latest", OffsetDateTime.class));
        });
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private Double round(Double value, int decimals) {
        if (value == null) {
            return null;
        }
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private List<String> toList(Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            String[] values = (String[]) array.getArray();
            return values == null ? List.of() : Arrays.asList(values);
        } catch (java.sql.SQLException ex) {
            return List.of();
        }
    }

    public record AlertRow(
            String gridId,
            double cellLat,
            double cellLon,
            Instant lastDetection,
            int nDetections,
            Double frpMaxMw,
            Double frpDensityMaxWm2,
            List<String> sensors,
            int confidenceMax
    ) {
    }
}
