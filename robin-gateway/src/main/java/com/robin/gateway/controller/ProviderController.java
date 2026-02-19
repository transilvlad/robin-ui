package com.robin.gateway.controller;

import com.robin.gateway.model.ProviderConfig;
import com.robin.gateway.service.ProviderConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Tag(name = "Provider Management", description = "APIs for managing DNS and Registrar provider configurations")
public class ProviderController {

    private final ProviderConfigService providerConfigService;
    private final com.robin.gateway.service.EncryptionService encryptionService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "List all providers", description = "Returns a paginated list of configured providers with sanitized credentials")
    public Mono<Page<Map<String, Object>>> getAllProviders(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return providerConfigService.getAllProviders(pageable)
                .map(p -> p.map(this::sanitizeProvider));
    }

    @PostMapping
    @Operation(summary = "Create a new provider", description = "Registers a new DNS or Registrar provider (e.g. Cloudflare, AWS Route53)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Provider created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public Mono<Map<String, Object>> createProvider(@Valid @RequestBody CreateProviderRequest request) {
        return providerConfigService.createProvider(request.getName(), request.getType(), request.getCredentials())
                .map(this::sanitizeProvider);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update provider", description = "Updates an existing provider configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Provider updated successfully"),
        @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public Mono<Map<String, Object>> updateProvider(
            @Parameter(description = "Internal provider ID") @PathVariable Long id, 
            @Valid @RequestBody CreateProviderRequest request) {
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

    @Schema(description = "Request for creating or updating a provider configuration")
    @Data
    public static class CreateProviderRequest {
        @Schema(description = "Display name for the provider", example = "Cloudflare Main Account")
        @NotBlank(message = "Name is required")
        private String name;
        
        @Schema(description = "Type of the provider")
        @NotNull(message = "Type is required")
        private ProviderConfig.ProviderType type;
        
        @Schema(description = "Provider credentials (e.g. apiToken, apiKey, secret). Key-value pairs depend on provider type.")
        @NotNull(message = "Credentials are required")
        private Map<String, String> credentials;
    }
}
