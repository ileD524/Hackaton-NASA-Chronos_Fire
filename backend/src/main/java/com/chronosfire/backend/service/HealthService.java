package com.chronosfire.backend.service;

import com.chronosfire.backend.dto.HealthResponse;
import com.chronosfire.backend.repository.HealthRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final HealthRepository repository;

    public HealthService(HealthRepository repository) {
        this.repository = repository;
    }

    public HealthResponse getHealth() {
        long start = System.nanoTime();
        boolean database = false;
        String version = null;
        Instant lastDetectionAt = null;
        long detectionsApprox = 0L;

        try {
            version = repository.databaseVersion();
            lastDetectionAt = repository.lastDetectionAt();
            detectionsApprox = repository.approximateDetections();
            database = true;
        } catch (DataAccessException ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
        }

        long latencyMs = (System.nanoTime() - start) / 1_000_000L;
        return new HealthResponse(
                database ? "ok" : "degraded",
                database,
                version,
                latencyMs,
                lastDetectionAt,
                detectionsApprox);
    }
}
