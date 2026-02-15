package com.robin.gateway.controller;

import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DnsRecordRepository;
import com.robin.gateway.service.DomainService;
import com.robin.gateway.service.DomainSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;
    private final DomainSyncService domainSyncService;
    private final DnsRecordRepository dnsRecordRepository;
    private final com.robin.gateway.service.DnsDiscoveryService dnsDiscoveryService;

    @GetMapping
    public Mono<Page<Domain>> getAllDomains(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return domainService.getAllDomains(pageable)
                .doOnError(e -> System.err.println("Error in getAllDomains controller: " + e.getMessage()));
    }

    @GetMapping("/{id}")
    public Mono<Domain> getDomain(@PathVariable Long id) {
        return domainService.getDomainById(id);
    }

    @PostMapping("/discover")
    public Mono<com.robin.gateway.service.DnsDiscoveryService.DiscoveryResult> discoverDomain(@Valid @RequestBody DiscoverDomainRequest request) {
        return dnsDiscoveryService.discover(request.getDomain(), request.getDnsProviderId());
    }

    @PostMapping
    public Mono<Domain> createDomain(@Valid @RequestBody CreateDomainRequest request) {
        return domainService.createDomain(
            request.getDomain(), 
            request.getDnsProviderId(), 
            request.getRegistrarProviderId(),
            request.getEmailProviderId(),
            request.getConfig(),
            request.getInitialRecords()
        );
    }

    @PutMapping("/{id}")
    public Mono<Domain> updateDomain(@PathVariable Long id, @Valid @RequestBody Domain domain) {
        return domainService.updateDomain(id, domain);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteDomain(@PathVariable Long id) {
        return domainService.deleteDomain(id);
    }

    @Data
    public static class DiscoverDomainRequest {
        @NotBlank(message = "Domain is required")
        private String domain;
        
        @NotNull(message = "DNS Provider ID is required")
        private Long dnsProviderId;
    }

    @Data
    public static class CreateDomainRequest {
        @NotBlank(message = "Domain is required")
        private String domain;
        
        @NotNull(message = "DNS Provider ID is required")
        private Long dnsProviderId;
        
        private Long registrarProviderId;
        private Long emailProviderId;
        private Domain config;
        private List<InitialRecordRequest> initialRecords;
    }

    @Data
    public static class InitialRecordRequest {
        @NotNull(message = "Type is required")
        private com.robin.gateway.model.DnsRecord.RecordType type;
        
        @NotBlank(message = "Name is required")
        private String name;
        
        @NotBlank(message = "Content is required")
        private String content;
        
        private Integer ttl;
        private Integer priority;
        private com.robin.gateway.model.DnsRecord.RecordPurpose purpose;
    }

    @GetMapping("/{id}/records")
    public Mono<List<com.robin.gateway.model.DnsRecord>> getRecords(@PathVariable Long id) {
        return Mono.fromCallable(() -> dnsRecordRepository.findByDomain_Id(id))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @PostMapping("/{id}/sync")
    public Mono<Void> syncDomain(@PathVariable Long id) {
        return domainSyncService.syncDomain(id);
    }

    @GetMapping("/{id}/dnssec")
    public Mono<List<com.robin.gateway.model.DnsRecord>> getDnssecStatus(@PathVariable Long id) {
        return domainService.getDnssecStatus(id);
    }

    @PostMapping("/{id}/dnssec/enable")
    public Mono<Void> enableDnssec(@PathVariable Long id) {
        return domainService.enableDnssec(id);
    }

    @PostMapping("/{id}/dnssec/disable")
    public Mono<Void> disableDnssec(@PathVariable Long id) {
        return domainService.disableDnssec(id);
    }
}