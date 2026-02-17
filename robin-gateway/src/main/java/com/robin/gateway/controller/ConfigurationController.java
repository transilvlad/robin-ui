package com.robin.gateway.controller;

import com.robin.gateway.service.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Tag(name = "Configuration Management", description = "APIs for managing Robin MTA configuration sections")
public class ConfigurationController {

    private final ConfigurationService configService;

    @GetMapping("/{section}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get config section", description = "Retrieves a configuration section (e.g. 'storage', 'relay') as a JSON object")
    public Mono<ResponseEntity<Map<String, Object>>> getConfig(
            @Parameter(description = "Configuration section name") @PathVariable String section) {
        return configService.getConfig(section)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{section}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update config section", description = "Updates a configuration section and triggers a reload on the MTA")
    public Mono<ResponseEntity<Void>> updateConfig(
            @Parameter(description = "Configuration section name") @PathVariable String section, 
            @Valid @RequestBody Map<String, Object> config) {
        return configService.updateConfig(section, config)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }
}
