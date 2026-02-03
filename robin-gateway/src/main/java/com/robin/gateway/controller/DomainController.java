package com.robin.gateway.controller;

import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DnsRecordRepository;
import com.robin.gateway.service.DomainService;
import com.robin.gateway.service.DomainSyncService;
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

    @PostMapping
    public Mono<Domain> createDomain(@RequestBody CreateDomainRequest request) {
        return domainService.createDomain(request.getDomain(), request.getDnsProviderId(), request.getRegistrarProviderId());
    }

    @PutMapping("/{id}")
    public Mono<Domain> updateDomain(@PathVariable Long id, @RequestBody Domain domain) {
        return domainService.updateDomain(id, domain);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteDomain(@PathVariable Long id) {
        return domainService.deleteDomain(id);
    }

    @Data
    public static class CreateDomainRequest {
        private String domain;
        private Long dnsProviderId;
        private Long registrarProviderId;
    }

    @GetMapping("/{id}/records")
    public Mono<List<com.robin.gateway.model.DnsRecord>> getRecords(@PathVariable Long id) {
        return Mono.fromCallable(() -> dnsRecordRepository.findByDomainId(id))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @PostMapping("/{id}/sync")
    public Mono<Void> syncDomain(@PathVariable Long id) {
        return domainSyncService.syncDomain(id);
    }
}