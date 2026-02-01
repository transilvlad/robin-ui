package com.robin.gateway.controller;

import com.robin.gateway.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationService configService;

    @GetMapping("/{section}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> getConfig(@PathVariable String section) {
        return configService.getConfig(section)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{section}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> updateConfig(@PathVariable String section, @RequestBody Map<String, Object> config) {
        return configService.updateConfig(section, config)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }
}
