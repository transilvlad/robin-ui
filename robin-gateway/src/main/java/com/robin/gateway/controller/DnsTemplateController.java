package com.robin.gateway.controller;

import com.robin.gateway.model.DnsTemplate;
import com.robin.gateway.model.dto.DnsTemplateRequest;
import com.robin.gateway.repository.DnsTemplateRepository;
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
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dns-templates")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "DNS Templates", description = "Endpoints for managing reusable DNS record templates")
@SecurityRequirement(name = "Bearer Authentication")
public class DnsTemplateController {

    private final DnsTemplateRepository dnsTemplateRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_DNS_RECORDS') or hasRole('ADMIN')")
    @Operation(summary = "List DNS templates", description = "Get all DNS record templates")
    public Mono<ResponseEntity<List<DnsTemplate>>> listTemplates() {
        log.info("Listing DNS templates");
        return Mono.fromCallable(dnsTemplateRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error listing DNS templates", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_DNS_RECORDS') or hasRole('ADMIN')")
    @Operation(summary = "Get DNS template", description = "Retrieve a specific DNS template by its ID")
    public Mono<ResponseEntity<DnsTemplate>> getTemplate(@PathVariable Long id) {
        log.info("Getting DNS template with id: {}", id);
        return Mono.fromCallable(() -> dnsTemplateRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt
                        .map(t -> Mono.just(ResponseEntity.ok(t)))
                        .orElse(Mono.just(ResponseEntity.notFound().build())))
                .onErrorResume(e -> {
                    log.error("Error getting DNS template with id: {}", id, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_DNS_RECORDS') or hasRole('ADMIN')")
    @Operation(summary = "Create DNS template", description = "Create a new reusable DNS record template")
    public Mono<ResponseEntity<DnsTemplate>> createTemplate(@Valid @RequestBody DnsTemplateRequest request) {
        log.info("Creating DNS template: {}", request.getName());
        return Mono.fromCallable(() -> {
            DnsTemplate template = DnsTemplate.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .records(request.getRecords())
                    .build();
            return dnsTemplateRepository.save(template);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .map(template -> ResponseEntity.status(HttpStatus.CREATED).body(template))
                .onErrorResume(e -> {
                    log.error("Error creating DNS template: {}", request.getName(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_DNS_RECORDS') or hasRole('ADMIN')")
    @Operation(summary = "Update DNS template", description = "Update an existing DNS record template")
    public Mono<ResponseEntity<DnsTemplate>> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody DnsTemplateRequest request) {
        log.info("Updating DNS template with id: {}", id);
        return Mono.fromCallable(() -> {
            DnsTemplate template = dnsTemplateRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("DNS template not found: " + id));
            template.setName(request.getName());
            template.setDescription(request.getDescription());
            template.setRecords(request.getRecords());
            return dnsTemplateRepository.save(template);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error updating DNS template with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_DNS_RECORDS') or hasRole('ADMIN')")
    @Operation(summary = "Delete DNS template", description = "Remove a DNS record template")
    public Mono<ResponseEntity<Map<String, String>>> deleteTemplate(@PathVariable Long id) {
        log.info("Deleting DNS template with id: {}", id);
        return Mono.fromCallable(() -> {
            if (!dnsTemplateRepository.existsById(id)) {
                throw new RuntimeException("DNS template not found: " + id);
            }
            dnsTemplateRepository.deleteById(id);
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(ResponseEntity.ok(Map.of("message", "DNS template deleted successfully"))))
                .onErrorResume(e -> {
                    log.error("Error deleting DNS template with id: {}", id, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
