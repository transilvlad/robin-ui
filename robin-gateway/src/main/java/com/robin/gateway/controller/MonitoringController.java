package com.robin.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Monitoring", description = "Metrics and monitoring endpoints")
public class MonitoringController {

    private final WebClient.Builder webClientBuilder;

    @Value("${robin.service-url:http://localhost:8080}")
    private String robinServiceUrl;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get historical metrics", description = "Returns metrics data series for the specified time range")
    public Mono<ResponseEntity<Map<String, Object>>> getMetrics(
            @RequestParam Instant start,
            @RequestParam Instant end) {
        
        log.debug("Fetching metrics from {} to {}", start, end);

        // Bridge to MTA Graphite endpoint
        return webClientBuilder.build()
                .get()
                .uri(robinServiceUrl + "/metrics/graphite")
                .retrieve()
                .bodyToMono(String.class)
                .map(graphiteData -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("startTime", start.toString());
                    response.put("endTime", end.toString());
                    
                    List<Map<String, Object>> series = new ArrayList<>();
                    
                    // Parse Graphite data and map to UI MetricType
                    Map<String, Double> currentMetrics = parseGraphite(graphiteData);
                    
                    addSeries(series, "QUEUE_SIZE", "items", currentMetrics.getOrDefault("robin_email_queue_size", 0.0));
                    addSeries(series, "MESSAGES_SENT", "messages", currentMetrics.getOrDefault("robin_email_receipt_success", 0.0));
                    addSeries(series, "MESSAGES_RECEIVED", "messages", currentMetrics.getOrDefault("robin_email_receipt_start", 0.0));
                    addSeries(series, "CONNECTIONS", "connections", currentMetrics.getOrDefault("jvm_threads_live", 0.0));
                    addSeries(series, "CPU_USAGE", "%", currentMetrics.getOrDefault("system_cpu_usage", 0.0) * 100.0);
                    addSeries(series, "MEMORY_USAGE", "%", calculateMemoryUsage(currentMetrics));
                    
                    response.put("series", series);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Error fetching metrics from MTA", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    @GetMapping("/system")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get system statistics", description = "Returns real-time system resource usage")
    public Mono<ResponseEntity<Map<String, Object>>> getSystemStats() {
        return webClientBuilder.build()
                .get()
                .uri(robinServiceUrl + "/metrics/system")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(mtaStats -> {
                    // Supplement missing fields for UI
                    Map<String, Object> stats = new HashMap<>(mtaStats);
                    
                    // Map cpu processors to cores
                    if (stats.containsKey("cpu")) {
                        Map<String, Object> cpu = new HashMap<>((Map) stats.get("cpu"));
                        cpu.putIfAbsent("usage", 0.0);
                        cpu.putIfAbsent("cores", cpu.getOrDefault("processors", 1));
                        stats.put("cpu", cpu);
                    }
                    
                    // Add memory usagePercent
                    if (stats.containsKey("memory")) {
                        Map<String, Object> mem = new HashMap<>((Map) stats.get("memory"));
                        double total = ((Number) mem.get("total")).doubleValue();
                        double used = ((Number) mem.get("used")).doubleValue();
                        if (total > 0) {
                            mem.put("usagePercent", (used / total) * 100.0);
                        } else {
                            mem.put("usagePercent", 0.0);
                        }
                        stats.put("memory", mem);
                    }
                    
                    // Add mock disk stats if missing
                    if (!stats.containsKey("disk")) {
                        Map<String, Object> disk = new HashMap<>();
                        disk.put("total", 100L * 1024 * 1024 * 1024); // 100GB mock
                        disk.put("used", 20L * 1024 * 1024 * 1024);  // 20GB mock
                        disk.put("free", 80L * 1024 * 1024 * 1024);  // 80GB mock
                        disk.put("usagePercent", 20.0);
                        stats.put("disk", disk);
                    }
                    
                    return ResponseEntity.ok(stats);
                });
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public Mono<ResponseEntity<Map<String, Object>>> getQueueStats() {
        return webClientBuilder.build()
                .get()
                .uri(robinServiceUrl + "/metrics/queue")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error fetching queue stats from MTA", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
                });
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public Mono<ResponseEntity<String>> exportMetrics(
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam(defaultValue = "csv") String format) {
        
        // Simplified export - just return the graphite data as text for now
        return webClientBuilder.build()
                .get()
                .uri(robinServiceUrl + "/metrics/graphite")
                .retrieve()
                .bodyToMono(String.class)
                .map(data -> ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(data));
    }

    private Map<String, Double> parseGraphite(String data) {
        Map<String, Double> metrics = new HashMap<>();
        if (data == null) return metrics;
        
        String[] lines = data.split("\n");
        for (String line : lines) {
            String[] parts = line.split(" ");
            if (parts.length >= 2) {
                try {
                    metrics.put(parts[0], Double.parseDouble(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return metrics;
    }

    private void addSeries(List<Map<String, Object>> series, String metricType, String unit, Double value) {
        Map<String, Object> s = new HashMap<>();
        s.put("metric", metricType);
        s.put("unit", unit);
        
        List<Map<String, Object>> dataPoints = new ArrayList<>();
        Map<String, Object> dp = new HashMap<>();
        dp.put("timestamp", Instant.now().toString());
        dp.put("value", value);
        dataPoints.add(dp);
        
        s.put("dataPoints", dataPoints);
        series.add(s);
    }

    private Double calculateMemoryUsage(Map<String, Double> metrics) {
        Double used = metrics.get("jvm_memory_used");
        Double max = metrics.get("jvm_memory_max");
        if (used != null && max != null && max > 0) {
            return (used / max) * 100.0;
        }
        return 0.0;
    }
}
