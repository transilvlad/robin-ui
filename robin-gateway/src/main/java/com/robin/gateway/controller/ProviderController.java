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

    @GetMapping
    public Mono<Page<ProviderConfig>> getAllProviders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return providerConfigService.getAllProviders(pageable)
                .map(p -> {
                    // Redact credentials in response
                    p.getContent().forEach(c -> c.setCredentials(null));
                    return p;
                });
    }

    @PostMapping
    public Mono<ProviderConfig> createProvider(@RequestBody CreateProviderRequest request) {
        return providerConfigService.createProvider(request.getName(), request.getType(), request.getCredentials())
                .map(p -> {
                    p.setCredentials(null);
                    return p;
                });
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
