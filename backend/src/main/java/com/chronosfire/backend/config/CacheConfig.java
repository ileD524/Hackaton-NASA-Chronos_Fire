package com.chronosfire.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache("meta", 10),
                cache("weekly", 10),
                cache("daily", 10),
                cache("heatmap", 10),
                cache("monthly", 10),
                cache("trend", 10),
                cache("validation", 10),
                // Las alertas son NRT: 60 s para no mostrar datos viejos.
                cache("alerts", 1)));
        return manager;
    }

    private CaffeineCache cache(String name, int minutes) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(minutes))
                .build());
    }
}
