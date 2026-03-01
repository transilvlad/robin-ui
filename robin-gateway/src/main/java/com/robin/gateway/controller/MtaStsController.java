package com.robin.gateway.controller;

import com.robin.gateway.model.MtaStsWorker;
import com.robin.gateway.model.dto.MtaStsPolicyModeRequest;
import com.robin.gateway.service.MtaStsService;
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

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/domains/{domainId}/mta-sts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "MTA-STS", description = "Endpoints for managing MTA-STS Cloudflare Worker deployments")
@SecurityRequirement(name = "Bearer Authentication")
public class MtaStsController {

    private final MtaStsService mtaStsService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_DOMAINS', 'MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Get MTA-STS worker status", description = "Retrieve the MTA-STS worker deployment status for a domain")
    public Mono<ResponseEntity<MtaStsWorker>> getWorkerStatus(@PathVariable Long domainId) {
        log.info("Getting MTA-STS worker status for domain id: {}", domainId);
        return mtaStsService.getWorkerForDomain(domainId)
                .map(opt -> opt
                        .<ResponseEntity<MtaStsWorker>>map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build()))
                .onErrorResume(e -> {
                    log.error("Error getting MTA-STS worker status for domain id: {}", domainId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping("/deploy")
    @PreAuthorize("hasAuthority('MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Deploy MTA-STS worker", description = "Initiate the deployment of a Cloudflare Worker for MTA-STS")
    public Mono<ResponseEntity<MtaStsWorker>> deployWorker(@PathVariable Long domainId) {
        log.info("Deploying MTA-STS worker for domain id: {}", domainId);
        return mtaStsService.initiateWorkerDeployment(domainId)
                .map(worker -> ResponseEntity.status(HttpStatus.CREATED).body(worker))
                .onErrorResume(e -> {
                    log.error("Error deploying MTA-STS worker for domain id: {}", domainId, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PutMapping("/policy-mode")
    @PreAuthorize("hasAuthority('MANAGE_DOMAINS') or hasRole('ADMIN')")
    @Operation(summary = "Update MTA-STS policy mode", description = "Change the MTA-STS policy mode (testing/enforce/none)")
    public Mono<ResponseEntity<MtaStsWorker>> updatePolicyMode(
            @PathVariable Long domainId,
            @Valid @RequestBody MtaStsPolicyModeRequest request) {
        log.info("Updating MTA-STS policy mode to '{}' for domain id: {}", request.getPolicyMode(), domainId);
        return mtaStsService.updatePolicyMode(domainId, request.getPolicyMode())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error updating MTA-STS policy mode for domain id: {}", domainId, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
