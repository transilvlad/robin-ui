package com.robin.gateway.controller;

import com.robin.gateway.model.DomainDnsRecord;
import com.robin.gateway.model.dto.DomainDnsRecordRequest;
import com.robin.gateway.repository.DomainDnsRecordRepository;
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
@RequestMapping("/api/v1/domains/{domainId}/dns")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Domain DNS Records", description = "Endpoints for managing DNS records for a domain")
@SecurityRequirement(name = "Bearer Authentication")
public class DomainDnsController {

    private final DomainDnsRecordRepository domainDnsRecordRepository;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_DOMAINS', 'MANAGE_DNS_RECORDS') or hasRole('ADMIN')")
    @Operation(summary = "List DNS records", description = "Get all DNS records for a specific domain")
    public Mono<ResponseEntity<List<DomainDnsRecord>>> listRecords(@PathVariable Long domainId) {
        log.info("Listing DNS records for domain id: {}", domainId);
        return Mono.fromCallable(() -> domainDnsRecordRepository.findByDomainId(domainId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error listing DNS records for domain id: {}", domainId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_DNS_RECORDS') or hasRole('ADMIN')")
    @Operation(summary = "Create DNS record", description = "Add a new DNS record to a domain")
    public Mono<ResponseEntity<DomainDnsRecord>> createRecord(
            @PathVariable Long domainId,
            @Valid @RequestBody DomainDnsRecordRequest request) {
        log.info("Creating DNS record (type={}, name={}) for domain id: {}", request.getRecordType(), request.getName(), domainId);
        return Mono.fromCallable(() -> {
            DomainDnsRecord record = DomainDnsRecord.builder()
                    .domainId(domainId)
                    .recordType(request.getRecordType())
                    .name(request.getName())
                    .value(request.getValue())
                    .ttl(request.getTtl())
                    .priority(request.getPriority())
                    .managed(request.getManaged())
                    .build();
            return domainDnsRecordRepository.save(record);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .map(record -> ResponseEntity.status(HttpStatus.CREATED).body(record))
                .onErrorResume(e -> {
                    log.error("Error creating DNS record for domain id: {}", domainId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PutMapping("/{recordId}")
    @PreAuthorize("hasAuthority('MANAGE_DNS_RECORDS') or hasRole('ADMIN')")
    @Operation(summary = "Update DNS record", description = "Update an existing DNS record")
    public Mono<ResponseEntity<DomainDnsRecord>> updateRecord(
            @PathVariable Long domainId,
            @PathVariable Long recordId,
            @Valid @RequestBody DomainDnsRecordRequest request) {
        log.info("Updating DNS record {} for domain id: {}", recordId, domainId);
        return Mono.fromCallable(() -> {
            DomainDnsRecord record = domainDnsRecordRepository.findById(recordId)
                    .filter(r -> r.getDomainId().equals(domainId))
                    .orElseThrow(() -> new RuntimeException("DNS record not found: " + recordId));

            record.setRecordType(request.getRecordType());
            record.setName(request.getName());
            record.setValue(request.getValue());
            record.setTtl(request.getTtl());
            record.setPriority(request.getPriority());
            record.setManaged(request.getManaged());
            return domainDnsRecordRepository.save(record);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error updating DNS record {} for domain id: {}", recordId, domainId, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @DeleteMapping("/{recordId}")
    @PreAuthorize("hasAuthority('MANAGE_DNS_RECORDS') or hasRole('ADMIN')")
    @Operation(summary = "Delete DNS record", description = "Remove a DNS record from a domain")
    public Mono<ResponseEntity<Map<String, String>>> deleteRecord(
            @PathVariable Long domainId,
            @PathVariable Long recordId) {
        log.info("Deleting DNS record {} for domain id: {}", recordId, domainId);
        return Mono.fromCallable(() -> {
            DomainDnsRecord record = domainDnsRecordRepository.findById(recordId)
                    .filter(r -> r.getDomainId().equals(domainId))
                    .orElseThrow(() -> new RuntimeException("DNS record not found: " + recordId));
            domainDnsRecordRepository.delete(record);
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(ResponseEntity.ok(Map.of("message", "DNS record deleted successfully"))))
                .onErrorResume(e -> {
                    log.error("Error deleting DNS record {} for domain id: {}", recordId, domainId, e);
                    if (e.getMessage().contains("not found")) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
