package com.robin.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Monitoring", description = "Logging endpoints")
public class LoggingController {

    private final WebClient.Builder webClientBuilder;

    @Value("${ROBIN_CLIENT_URL:http://localhost:8090}")
    private String robinClientUrl;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get logs", description = "Returns log entries from the MTA")
    public Mono<ResponseEntity<Map<String, Object>>> getLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        String query = search != null ? search : (level != null ? level : ".");
        log.debug("Fetching logs from MTA with query: {}, limit: {}, offset: {}", query, limit, offset);
        
        return webClientBuilder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("http")
                        .host(extractHost(robinClientUrl))
                        .port(extractPort(robinClientUrl))
                        .path("/logs")
                        .queryParam("q", query)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(logData -> {
                    List<Map<String, Object>> entries = parseLogs(logData, level);
                    
                    int total = entries.size();
                    int startIdx = Math.min(offset, total);
                    int endIdx = Math.min(startIdx + limit, total);
                    List<Map<String, Object>> pagedEntries = new ArrayList<>(entries.subList(startIdx, endIdx));
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("entries", pagedEntries);
                    response.put("total", total);
                    response.put("hasMore", total > endIdx);
                    
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Error bridging logs from MTA: {}", e.getMessage());
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("entries", List.of());
                    errorResponse.put("total", 0);
                    errorResponse.put("hasMore", false);
                    return Mono.just(ResponseEntity.ok(errorResponse));
                });
    }

    @GetMapping("/loggers")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get loggers", description = "Returns available log sources")
    public Mono<ResponseEntity<List<String>>> getLoggers() {
        return Mono.just(ResponseEntity.ok(List.of(
                "com.mimecast.robin",
                "org.springframework",
                "root",
                "clamav",
                "rspamd",
                "dovecot"
        )));
    }

    private List<Map<String, Object>> parseLogs(String data, String filterLevel) {
        if (data == null || data.isEmpty()) return new ArrayList<>();
        
        List<Map<String, Object>> entries = new ArrayList<>();
        String[] lines = data.split("\n");
        
        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("Usage:")) continue;
            
            Map<String, Object> entry = new HashMap<>();
            entry.put("message", line);
            entry.put("timestamp", Instant.now().toString());
            entry.put("level", "INFO");
            entry.put("logger", "robin");
            
            if (line.contains(" ERROR ")) entry.put("level", "ERROR");
            else if (line.contains(" WARN ")) entry.put("level", "WARN");
            else if (line.contains(" DEBUG ")) entry.put("level", "DEBUG");
            else if (line.contains(" TRACE ")) entry.put("level", "TRACE");
            
            if (filterLevel == null || entry.get("level").equals(filterLevel)) {
                entries.add(entry);
            }
        }
        
        Collections.reverse(entries);
        return entries;
    }

    private String extractHost(String url) {
        if (url == null) return "localhost";
        String host = url.replace("http://", "").replace("https://", "");
        if (host.contains(":")) {
            host = host.substring(0, host.indexOf(":"));
        }
        if (host.contains("/")) {
            host = host.substring(0, host.indexOf("/"));
        }
        return host;
    }

    private int extractPort(String url) {
        if (url == null) return 8090;
        String host = url.replace("http://", "").replace("https://", "");
        if (host.contains(":")) {
            String portStr = host.substring(host.indexOf(":") + 1);
            if (portStr.contains("/")) {
                portStr = portStr.substring(0, portStr.indexOf("/"));
            }
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                return 8090;
            }
        }
        return 80;
    }
}
