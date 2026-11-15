package com.chronosfire.backend.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HealthRepository {

    private final JdbcTemplate jdbc;

    public HealthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String databaseVersion() {
        return jdbc.queryForObject("SELECT version()", String.class);
    }

    /**
     * Usa el indice de acq_date y devuelve la ultima deteccion real.
     */
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

    /**
     * Aproximacion de pg_class: evita un count(*) completo en /api/health.
     */
    public long approximateDetections() {
        Long value = jdbc.queryForObject(
                "SELECT reltuples::bigint FROM pg_class WHERE relname = 'detections'",
                Long.class);
        return value == null ? 0L : value;
    }
}
