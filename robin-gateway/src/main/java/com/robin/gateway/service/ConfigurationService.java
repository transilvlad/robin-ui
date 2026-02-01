package com.robin.gateway.service;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing Robin MTA configuration files.
 * Reads/Writes JSON5 files from the shared configuration volume.
 * Reactive implementation.
 */
@Service
@Slf4j
public class ConfigurationService {

    @Value("${robin.config-path:cfg/}")
    private String configPath;

    @Value("${robin.service-url:http://localhost:8080}")
    private String robinServiceUrl;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public ConfigurationService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
        // Initialize Jackson with JSON5-like support (comments, etc)
        this.objectMapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    @PostConstruct
    public void init() {
        File cfgDir = new File(configPath);
        if (!cfgDir.exists()) {
            log.warn("Configuration directory {} does not exist. Creating it.", configPath);
            cfgDir.mkdirs();
        }
    }

    /**
     * Get configuration section (e.g. "storage", "relay").
     *
     * @param section the configuration section name (filename without extension)
     * @return the configuration map
     */
    public Mono<Map<String, Object>> getConfig(String section) {
        return Mono.fromCallable(() -> readConfigFromFile(section))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Update configuration section.
     *
     * @param section the configuration section name
     * @param newConfig the new configuration map
     */
    public Mono<Void> updateConfig(String section, Map<String, Object> newConfig) {
        return Mono.fromRunnable(() -> writeConfigToFile(section, newConfig))
                .subscribeOn(Schedulers.boundedElastic())
                .then(triggerReload());
    }

    private Map<String, Object> readConfigFromFile(String section) {
        File file = new File(configPath, section + ".json5");
        if (!file.exists()) {
            // Try .json
            file = new File(configPath, section + ".json");
            if (!file.exists()) {
                throw new RuntimeException("Configuration file for '" + section + "' not found.");
            }
        }

        try {
            return objectMapper.readValue(file, Map.class);
        } catch (Exception e) {
            log.error("Failed to read config file: {}", file.getAbsolutePath(), e);
            throw new RuntimeException("Failed to read configuration", e);
        }
    }

    private void writeConfigToFile(String section, Map<String, Object> content) {
        // Default to json5
        File file = new File(configPath, section + ".json5");
        
        try {
            objectMapper.writeValue(file, content);
            log.info("Updated configuration file: {}", file.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write config file: {}", file.getAbsolutePath(), e);
            throw new RuntimeException("Failed to write configuration", e);
        }
    }

    private Mono<Void> triggerReload() {
        return webClientBuilder.build()
                .post()
                .uri(robinServiceUrl + "/config/reload")
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(v -> log.info("Triggered config reload on Robin Server"))
                .doOnError(e -> log.error("Failed to trigger config reload: {}", e.getMessage()))
                .then();
    }
}
