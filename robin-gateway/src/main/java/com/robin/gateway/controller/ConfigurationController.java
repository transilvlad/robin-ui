package com.robin.gateway.controller;

import com.robin.gateway.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationService configService;

    @GetMapping("/{section}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String section) {
        return ResponseEntity.ok(configService.getConfig(section));
    }

    @PutMapping("/{section}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateConfig(@PathVariable String section, @RequestBody Map<String, Object> config) {
        configService.updateConfig(section, config);
        return ResponseEntity.ok().build();
    }
}
