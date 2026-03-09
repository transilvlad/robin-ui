package com.robin.gateway.controller;

import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.dto.DkimGenerateRequest;
import com.robin.gateway.service.DkimService;
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
@RequestMapping("/api/v1/domains/{domainId}/dkim")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Domain DKIM", description = "Endpoints for managing DKIM keys for a domain")
@SecurityRequirement(name = "Bearer Authentication")
public class DomainDkimController {

    private final DkimService dkimService;

    @GetMapping("/keys")
    @PreAuthorize("hasAuthority('MANAGE_DKIM') or hasRole('ADMIN')")
    @Operation(summary = "List DKIM keys", description = "Get all DKIM keys for a domain (private key masked)")
    public Mono<ResponseEntity<List<DkimKey>>> listKeys(@PathVariable Long domainId) {
        log.info("Listing DKIM keys for domain id: {}", domainId);
        return dkimService.getKeysForDomain(domainId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error listing DKIM keys for domain id: {}", domainId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('MANAGE_DKIM') or hasRole('ADMIN')")
    @Operation(summary = "Generate DKIM key", description = "Generate a new DKIM key pair for a domain")
    public Mono<ResponseEntity<DkimKey>> generateKey(
            @PathVariable Long domainId,
            @Valid @RequestBody DkimGenerateRequest request) {
        log.info("Generating DKIM key for domain id: {} with algorithm: {}", domainId, request.getAlgorithm());
        return dkimService.generateKeyPair(domainId, request.getAlgorithm(), request.getSelector())
                .map(key -> ResponseEntity.status(HttpStatus.CREATED).body(key))
                .onErrorResume(e -> {
                    log.error("Error generating DKIM key for domain id: {}", domainId, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping("/rotate")
    @PreAuthorize("hasAuthority('MANAGE_DKIM') or hasRole('ADMIN')")
    @Operation(summary = "Rotate DKIM key", description = "Initiate DKIM key rotation; returns the new key")
    public Mono<ResponseEntity<DkimKey>> rotateKey(@PathVariable Long domainId) {
        log.info("Initiating DKIM key rotation for domain id: {}", domainId);
        return dkimService.initiateRotation(domainId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error rotating DKIM key for domain id: {}", domainId, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @DeleteMapping("/keys/{keyId}")
    @PreAuthorize("hasAuthority('MANAGE_DKIM') or hasRole('ADMIN')")
    @Operation(summary = "Retire DKIM key", description = "Mark a DKIM key as retired")
    public Mono<ResponseEntity<Map<String, String>>> retireKey(
            @PathVariable Long domainId,
            @PathVariable Long keyId) {
        log.info("Retiring DKIM key {} for domain id: {}", keyId, domainId);
        return dkimService.retireKey(keyId)
                .then(Mono.just(ResponseEntity.ok(Map.of("message", "DKIM key retired successfully"))))
                .onErrorResume(e -> {
                    log.error("Error retiring DKIM key {} for domain id: {}", keyId, domainId, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
