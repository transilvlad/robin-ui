package com.robin.gateway.controller;

import com.robin.gateway.model.Alias;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.dto.AliasRequest;
import com.robin.gateway.model.dto.DomainRequest;
import com.robin.gateway.service.DomainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/domains")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Domain Management", description = "Endpoints for managing email domains and aliases")
@SecurityRequirement(name = "Bearer Authentication")
public class DomainController {

    private final DomainService domainService;

    // ===== Domain Endpoints =====

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_DOMAINS', 'MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "List all domains", description = "Get all email domains with pagination")
    public Mono<ResponseEntity<Page<Domain>>> listDomains(@PageableDefault(size = 20) Pageable pageable) {
        log.info("Listing domains - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return domainService.getAllDomains(pageable)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error listing domains", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('VIEW_DOMAINS', 'MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Get domain by ID", description = "Retrieve a specific domain by its ID")
    public Mono<ResponseEntity<Domain>> getDomain(@PathVariable Long id) {
        log.info("Getting domain with id: {}", id);
        return domainService.getDomainById(id)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error getting domain with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAnyAuthority('VIEW_DOMAINS', 'MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Get domain summary", description = "Retrieve a domain overview with health summary")
    public Mono<ResponseEntity<com.robin.gateway.model.dto.DomainSummary>> getDomainSummary(@PathVariable Long id) {
        log.info("Getting domain summary with id: {}", id);
        return domainService.getDomainSummary(id)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error getting domain summary with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Create domain", description = "Create a new email domain")
    public Mono<ResponseEntity<Domain>> createDomain(@Valid @RequestBody DomainRequest request) {
        log.info("Creating domain: {}", request.getDomain());
        return domainService.createDomain(request)
                .map(domain -> ResponseEntity.status(HttpStatus.CREATED).body(domain))
                .onErrorResume(e -> {
                    log.error("Error creating domain: {}", request.getDomain(), e);
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Delete domain", description = "Delete an email domain and all its aliases")
    public Mono<ResponseEntity<Map<String, String>>> deleteDomain(@PathVariable Long id) {
        log.info("Deleting domain with id: {}", id);
        return domainService.deleteDomain(id)
                .then(Mono.just(ResponseEntity.ok(Map.of("message", "Domain deleted successfully"))))
                .onErrorResume(e -> {
                    log.error("Error deleting domain with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ===== Alias Endpoints =====

    @GetMapping("/{domainId}/aliases")
    @PreAuthorize("hasAnyAuthority('VIEW_DOMAINS', 'MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "List domain aliases", description = "Get all aliases for a specific domain")
    public Mono<ResponseEntity<List<Alias>>> listDomainAliases(@PathVariable Long domainId) {
        log.info("Listing aliases for domain id: {}", domainId);
        return domainService.getAliasesByDomain(domainId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error listing aliases for domain id: {}", domainId, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @GetMapping("/aliases")
    @PreAuthorize("hasAnyAuthority('VIEW_DOMAINS', 'MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "List all aliases", description = "Get all email aliases with pagination")
    public Mono<ResponseEntity<Page<Alias>>> listAllAliases(@PageableDefault(size = 20) Pageable pageable) {
        log.info("Listing all aliases - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return domainService.getAllAliases(pageable)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error listing aliases", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @GetMapping("/aliases/{id}")
    @PreAuthorize("hasAnyAuthority('VIEW_DOMAINS', 'MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Get alias by ID", description = "Retrieve a specific alias by its ID")
    public Mono<ResponseEntity<Alias>> getAlias(@PathVariable Long id) {
        log.info("Getting alias with id: {}", id);
        return domainService.getAliasById(id)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error getting alias with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping("/aliases")
    @PreAuthorize("hasAuthority('MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Create alias", description = "Create a new email alias")
    public Mono<ResponseEntity<Alias>> createAlias(@Valid @RequestBody AliasRequest request) {
        log.info("Creating alias: {} -> {}", request.getSource(), request.getDestination());
        return domainService.createAlias(request.getSource(), request.getDestination())
                .map(alias -> ResponseEntity.status(HttpStatus.CREATED).body(alias))
                .onErrorResume(e -> {
                    log.error("Error creating alias: {} -> {}", request.getSource(), request.getDestination(), e);
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PutMapping("/aliases/{id}")
    @PreAuthorize("hasAuthority('MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Update alias", description = "Update the destination of an existing alias")
    public Mono<ResponseEntity<Alias>> updateAlias(
            @PathVariable Long id,
            @RequestParam String destination) {
        log.info("Updating alias id: {} with new destination: {}", id, destination);
        return domainService.updateAlias(id, destination)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error updating alias with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @DeleteMapping("/aliases/{id}")
    @PreAuthorize("hasAuthority('MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Delete alias", description = "Delete an email alias")
    public Mono<ResponseEntity<Map<String, String>>> deleteAlias(@PathVariable Long id) {
        log.info("Deleting alias with id: {}", id);
        return domainService.deleteAlias(id)
                .then(Mono.just(ResponseEntity.ok(Map.of("message", "Alias deleted successfully"))))
                .onErrorResume(e -> {
                    log.error("Error deleting alias with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
