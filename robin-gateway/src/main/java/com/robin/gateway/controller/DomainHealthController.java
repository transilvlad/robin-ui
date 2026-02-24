package com.robin.gateway.controller;

import com.robin.gateway.model.DomainHealth;
import com.robin.gateway.service.DomainHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/domains/{domainId}/health")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Domain Health", description = "Endpoints for checking domain email health status")
@SecurityRequirement(name = "Bearer Authentication")
public class DomainHealthController {

    private final DomainHealthService domainHealthService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get domain health", description = "Retrieve the latest health check results for a domain")
    public Mono<ResponseEntity<List<DomainHealth>>> getHealth(@PathVariable Long domainId) {
        log.info("Getting health status for domain id: {}", domainId);
        return domainHealthService.getHealthForDomain(domainId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error getting health for domain id: {}", domainId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger health verification", description = "Run all health checks for a domain immediately")
    public Mono<ResponseEntity<List<DomainHealth>>> triggerVerification(@PathVariable Long domainId) {
        log.info("Triggering health verification for domain id: {}", domainId);
        return domainHealthService.runHealthChecksForDomain(domainId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error triggering health verification for domain id: {}", domainId, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
