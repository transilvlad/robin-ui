package com.robin.gateway.service;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DnsRecordRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.service.dns.DnsProvider;
import com.robin.gateway.service.dns.DnsProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainSyncService {

    private final DomainRepository domainRepository;
    private final DnsRecordRepository dnsRecordRepository;
    private final DnsRecordGenerator dnsRecordGenerator;
    private final DnsProviderFactory dnsProviderFactory;

    @Transactional
    public Mono<Void> syncDomain(Long domainId) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            List<DnsRecord> expectedRecords = dnsRecordGenerator.generateExpectedRecords(domain);
            DnsProvider provider = dnsProviderFactory.getProvider(domain.getDnsProviderType());
            
            // Logic for diffing and syncing
            // For now, just save expected to local DB
            dnsRecordRepository.deleteByDomain(domain);
            dnsRecordRepository.saveAll(expectedRecords);
            
            // Sync to external provider if not MANUAL
            if (domain.getDnsProviderType() != Domain.DnsProviderType.MANUAL) {
                for (DnsRecord record : expectedRecords) {
                    provider.createRecord(domain, record);
                }
            }
            
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
