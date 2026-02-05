package com.robin.gateway.controller;

import com.robin.gateway.model.ProviderConfig;
import com.robin.gateway.service.ProviderConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderConfigService providerConfigService;
    private final com.robin.gateway.service.EncryptionService encryptionService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping
    public Mono<Page<Map<String, Object>>> getAllProviders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return providerConfigService.getAllProviders(pageable)
                .map(p -> p.map(this::sanitizeProvider));
    }

    @PostMapping
    public Mono<Map<String, Object>> createProvider(@RequestBody CreateProviderRequest request) {
        return providerConfigService.createProvider(request.getName(), request.getType(), request.getCredentials())
                .map(this::sanitizeProvider);
    }

    @PutMapping("/{id}")
    public Mono<Map<String, Object>> updateProvider(@PathVariable Long id, @RequestBody CreateProviderRequest request) {
        return providerConfigService.updateProvider(id, request.getName(), request.getType(), request.getCredentials())
                .map(this::sanitizeProvider);
    }

    private Map<String, Object> sanitizeProvider(ProviderConfig config) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", config.getId());
        map.put("name", config.getName());
        map.put("type", config.getType());
        map.put("createdAt", config.getCreatedAt());
        
        // Parse and sanitize credentials
        Map<String, String> sanitizedCreds = new java.util.HashMap<>();
        if (config.getCredentials() != null && !config.getCredentials().isEmpty()) {
            try {
                String decrypted = encryptionService.decrypt(config.getCredentials());
                Map<String, String> actualCreds = objectMapper.readValue(decrypted, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                actualCreds.forEach((k, v) -> {
                    if (isSensitive(k)) {
                        sanitizedCreds.put(k, "********");
                    } else {
                        sanitizedCreds.put(k, v);
                    }
                });
            } catch (Exception e) {
                // Ignore parsing errors for sanitization
            }
        }
        map.put("credentials", sanitizedCreds);
        return map;
    }

    private boolean isSensitive(String key) {
        String k = key.toLowerCase();
        return k.contains("token") || k.contains("secret") || k.contains("key") || k.contains("password");
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteProvider(@PathVariable Long id) {
        return providerConfigService.deleteProvider(id);
    }

    @Data
    public static class CreateProviderRequest {
        private String name;
        private ProviderConfig.ProviderType type;
        private Map<String, String> credentials;
    }
}
