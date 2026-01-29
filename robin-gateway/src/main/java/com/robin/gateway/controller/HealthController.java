package com.robin.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

    private final WebClient.Builder webClientBuilder;
    private final DataSource dataSource;
    private final ReactiveRedisConnectionFactory redisConnectionFactory;

    @Value("${robin.service-url:http://localhost:8080}")
    private String robinServiceUrl;

    @Value("${ROBIN_CLIENT_URL:http://localhost:8090}")
    private String robinClientUrl;

    @GetMapping("/aggregate")
    @Operation(summary = "Aggregated health check", description = "Get combined health status of all system components")
    public Mono<ResponseEntity<Map<String, Object>>> getAggregatedHealth() {
        log.debug("Aggregating health from all components");

        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("timestamp", System.currentTimeMillis());
        healthStatus.put("service", "robin-gateway");

        // Run all health checks in parallel
        return Mono.zip(
                checkRobinClientHealth(),
                checkRobinServiceHealth(),
                checkDatabaseHealth(),
                checkRedisHealth()
        ).map(tuple -> {
            healthStatus.put("robinClientApi", tuple.getT1());
            healthStatus.put("robinServiceApi", tuple.getT2());
            healthStatus.put("database", tuple.getT3());
            healthStatus.put("redis", tuple.getT4());

            // Determine overall status
            boolean allHealthy = tuple.getT1().get("status").equals("UP")
                    && tuple.getT2().get("status").equals("UP")
                    && tuple.getT3().get("status").equals("UP")
                    && tuple.getT4().get("status").equals("UP");

            healthStatus.put("status", allHealthy ? "UP" : "DEGRADED");

            HttpStatus status = allHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(healthStatus);
        }).onErrorResume(e -> {
            log.error("Error aggregating health", e);
            healthStatus.put("status", "DOWN");
            healthStatus.put("error", e.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(healthStatus));
        });
    }

    /**
     * Check Robin Client API (port 8090) health
     */
    private Mono<Map<String, Object>> checkRobinClientHealth() {
        return webClientBuilder.build()
                .get()
                .uri(robinClientUrl + "/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> health = new HashMap<>();
                    health.put("status", "UP");
                    health.put("url", robinClientUrl);
                    health.put("response", response);
                    return health;
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Robin Client API health check failed: {}", e.getMessage());
                    Map<String, Object> health = new HashMap<>();
                    health.put("status", "DOWN");
                    health.put("url", robinClientUrl);
                    health.put("error", e.getMessage());
                    return Mono.just(health);
                });
    }

    /**
     * Check Robin Service API (port 8080) health
     */
    private Mono<Map<String, Object>> checkRobinServiceHealth() {
        return webClientBuilder.build()
                .get()
                .uri(robinServiceUrl + "/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> health = new HashMap<>();
                    health.put("status", "UP");
                    health.put("url", robinServiceUrl);
                    health.put("response", response);
                    return health;
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Robin Service API health check failed: {}", e.getMessage());
                    Map<String, Object> health = new HashMap<>();
                    health.put("status", "DOWN");
                    health.put("url", robinServiceUrl);
                    health.put("error", e.getMessage());
                    return Mono.just(health);
                });
    }

    /**
     * Check database connectivity
     */
    private Mono<Map<String, Object>> checkDatabaseHealth() {
        return Mono.fromCallable(() -> {
            Map<String, Object> health = new HashMap<>();
            try (Connection connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(2);
                health.put("status", isValid ? "UP" : "DOWN");
                health.put("database", connection.getMetaData().getDatabaseProductName());
                health.put("url", connection.getMetaData().getURL());
            } catch (Exception e) {
                log.warn("Database health check failed", e);
                health.put("status", "DOWN");
                health.put("error", e.getMessage());
            }
            return health;
        });
    }

    /**
     * Check Redis connectivity
     */
    private Mono<Map<String, Object>> checkRedisHealth() {
        return Mono.fromCallable(() -> {
                    var connection = redisConnectionFactory.getReactiveConnection();
                    Map<String, Object> health = new HashMap<>();
                    health.put("status", "UP");
                    health.put("ping", "PONG");
                    return health;
                })
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(e -> {
                    log.warn("Redis health check failed: {}", e.getMessage());
                    Map<String, Object> health = new HashMap<>();
                    health.put("status", "DOWN");
                    health.put("error", e.getMessage());
                    return Mono.just(health);
                });
    }
}
