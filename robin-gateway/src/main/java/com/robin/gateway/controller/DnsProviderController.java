package com.robin.gateway.controller;

import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.dto.DnsProviderRequest;
import com.robin.gateway.service.DnsProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dns-providers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "DNS Providers", description = "Endpoints for managing DNS provider integrations")
@SecurityRequirement(name = "Bearer Authentication")
public class DnsProviderController {

    private final DnsProviderService dnsProviderService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MANAGE_DNS_PROVIDERS') or hasRole('ADMIN')")
    @Operation(summary = "List DNS providers", description = "Get all configured DNS providers with masked credentials")
    public Mono<ResponseEntity<List<DnsProvider>>> listProviders() {
        log.info("Listing DNS providers");
        return dnsProviderService.getAllProviders()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error listing DNS providers", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MANAGE_DNS_PROVIDERS') or hasRole('ADMIN')")
    @Operation(summary = "Get DNS provider", description = "Retrieve a specific DNS provider by its ID")
    public Mono<ResponseEntity<DnsProvider>> getProvider(@PathVariable Long id) {
        log.info("Getting DNS provider with id: {}", id);
        return dnsProviderService.getProviderById(id)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error getting DNS provider with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_DNS_PROVIDERS') or hasRole('ADMIN')")
    @Operation(summary = "Create DNS provider", description = "Register a new DNS provider with encrypted credentials")
    public Mono<ResponseEntity<DnsProvider>> createProvider(@Valid @RequestBody DnsProviderRequest request) {
        log.info("Creating DNS provider: {}", request.getName());
        return dnsProviderService.createProvider(request)
                .map(provider -> ResponseEntity.status(HttpStatus.CREATED).body(provider))
                .onErrorResume(e -> {
                    log.error("Error creating DNS provider: {}", request.getName(), e);
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_DNS_PROVIDERS') or hasRole('ADMIN')")
    @Operation(summary = "Update DNS provider", description = "Update an existing DNS provider's configuration")
    public Mono<ResponseEntity<DnsProvider>> updateProvider(
            @PathVariable Long id,
            @Valid @RequestBody DnsProviderRequest request) {
        log.info("Updating DNS provider with id: {}", id);
        return dnsProviderService.updateProvider(id, request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error updating DNS provider with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_DNS_PROVIDERS') or hasRole('ADMIN')")
    @Operation(summary = "Delete DNS provider", description = "Remove a DNS provider (fails if in use by a domain)")
    public Mono<ResponseEntity<Map<String, String>>> deleteProvider(@PathVariable Long id) {
        log.info("Deleting DNS provider with id: {}", id);
        return dnsProviderService.deleteProvider(id)
                .then(Mono.just(ResponseEntity.ok(Map.of("message", "DNS provider deleted successfully"))))
                .onErrorResume(e -> {
                    log.error("Error deleting DNS provider with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    if (e instanceof IllegalStateException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .<Map<String, String>>build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('MANAGE_DNS_PROVIDERS') or hasRole('ADMIN')")
    @Operation(summary = "Test DNS provider connection", description = "Verify connectivity to the DNS provider API")
    public Mono<ResponseEntity<Map<String, Boolean>>> testConnection(@PathVariable Long id) {
        log.info("Testing connection for DNS provider with id: {}", id);
        return dnsProviderService.testConnection(id)
                .map(connected -> ResponseEntity.ok(Map.of("connected", connected)))
                .onErrorResume(e -> {
                    log.error("Error testing connection for DNS provider with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.ok(Map.of("connected", false)));
                });
    }
}
