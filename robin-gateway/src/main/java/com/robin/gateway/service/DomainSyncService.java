package com.robin.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.integration.cloudflare.CloudflareApiClient;
import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.DnsProviderType;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.DomainDnsRecord;
import com.robin.gateway.repository.DnsProviderRepository;
import com.robin.gateway.repository.DomainDnsRecordRepository;
import com.robin.gateway.repository.DomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainSyncService {

    private final DomainDnsRecordRepository dnsRecordRepository;
    private final DomainRepository domainRepository;
    private final DnsProviderRepository dnsProviderRepository;
    private final CloudflareApiClient cloudflareApiClient;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    /**
     * Pushes all managed non-NS records for a domain to its configured Cloudflare DNS provider.
     * Records with a providerRecordId are updated; records without one are created and the
     * returned record ID is saved back to the local database.
     */
    public Mono<Map<String, Object>> syncManagedRecords(Long domainId) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            if (domain.getDnsProviderId() == null) {
                throw new RuntimeException("No DNS provider configured for this domain. Assign a DNS provider first.");
            }

            DnsProvider provider = dnsProviderRepository.findById(domain.getDnsProviderId())
                    .orElseThrow(() -> new RuntimeException("DNS provider not found: " + domain.getDnsProviderId()));

            if (provider.getType() != DnsProviderType.CLOUDFLARE) {
                throw new RuntimeException("DNS record sync is currently only supported for Cloudflare providers.");
            }

            String apiToken = extractApiToken(provider);
            List<DomainDnsRecord> records = dnsRecordRepository.findByDomainId(domainId).stream()
                    .filter(r -> Boolean.TRUE.equals(r.getManaged()) && !"NS".equals(r.getRecordType()))
                    .collect(Collectors.toList());

            log.info("Syncing {} managed DNS record(s) for domain {}", records.size(), domainId);
            return new SyncContext(domain, apiToken, records);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(ctx -> syncToCloudflare(ctx.domain().getDomain(), ctx.apiToken(), ctx.records()));
    }

    /**
     * Pushes all NS records for a domain to the specified Cloudflare provider.
     * If the domain's nsProviderId differs from the supplied one it is updated in the database.
     */
    public Mono<Map<String, Object>> syncNsRecords(Long domainId, Long nsProviderId) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            DnsProvider provider = dnsProviderRepository.findById(nsProviderId)
                    .orElseThrow(() -> new RuntimeException("NS provider not found: " + nsProviderId));

            if (provider.getType() != DnsProviderType.CLOUDFLARE) {
                throw new RuntimeException("NS record sync is currently only supported for Cloudflare providers.");
            }

            String apiToken = extractApiToken(provider);

            if (!nsProviderId.equals(domain.getNsProviderId())) {
                domain.setNsProviderId(nsProviderId);
                domainRepository.save(domain);
                log.info("Updated nsProviderId to {} for domain {}", nsProviderId, domainId);
            }

            List<DomainDnsRecord> records = dnsRecordRepository.findByDomainId(domainId).stream()
                    .filter(r -> "NS".equals(r.getRecordType()))
                    .collect(Collectors.toList());

            log.info("Syncing {} NS record(s) for domain {}", records.size(), domainId);
            return new SyncContext(domain, apiToken, records);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(ctx -> syncToCloudflare(ctx.domain().getDomain(), ctx.apiToken(), ctx.records()));
    }

    private Mono<Map<String, Object>> syncToCloudflare(String domainName, String apiToken, List<DomainDnsRecord> records) {
        if (records.isEmpty()) {
            return Mono.just(Map.of("synced", 0, "failed", 0, "errors", List.of()));
        }

        AtomicInteger synced = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        return cloudflareApiClient.getZoneId(domainName, apiToken)
                .flatMap(zoneId -> {
                    Mono<Void> chain = Mono.empty();
                    for (DomainDnsRecord record : records) {
                        Mono<Void> op = syncSingleRecord(zoneId, record, apiToken)
                                .doOnSuccess(v -> synced.incrementAndGet())
                                .onErrorResume(e -> {
                                    failed.incrementAndGet();
                                    errors.add(record.getRecordType() + " " + record.getName() + ": " + e.getMessage());
                                    log.warn("Sync failed for {} {}: {}", record.getRecordType(), record.getName(), e.getMessage());
                                    return Mono.empty();
                                });
                        chain = chain.then(op);
                    }
                    return chain.then(Mono.fromSupplier(() ->
                            Map.<String, Object>of("synced", synced.get(), "failed", failed.get(), "errors", errors)));
                });
    }

    private Mono<Void> syncSingleRecord(String zoneId, DomainDnsRecord record, String apiToken) {
        int ttl = record.getTtl() != null ? record.getTtl() : 1;

        if (record.getProviderRecordId() != null) {
            return cloudflareApiClient.updateDnsRecord(
                    zoneId, record.getProviderRecordId(),
                    record.getRecordType(), record.getName(), record.getValue(),
                    ttl, apiToken);
        }

        return cloudflareApiClient.createDnsRecord(
                zoneId, record.getRecordType(), record.getName(), record.getValue(),
                ttl, apiToken)
                .flatMap(recordId -> Mono.fromCallable(() -> {
                    record.setProviderRecordId(recordId);
                    dnsRecordRepository.save(record);
                    return null;
                }).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    /**
     * Deletes a DNS record locally and, if it is a managed record that was previously synced
     * (i.e. has a providerRecordId), also removes it from Cloudflare before deleting locally.
     */
    public Mono<Void> deleteDnsRecord(Long domainId, Long recordId) {
        return Mono.fromCallable(() -> dnsRecordRepository.findById(recordId)
                        .filter(r -> r.getDomainId().equals(domainId))
                        .orElseThrow(() -> new RuntimeException("DNS record not found: " + recordId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(record -> {
                    if (Boolean.TRUE.equals(record.getManaged()) && record.getProviderRecordId() != null) {
                        return deleteFromCloudflare(domainId, record)
                                .then(Mono.fromCallable(() -> {
                                    dnsRecordRepository.delete(record);
                                    return null;
                                }).subscribeOn(Schedulers.boundedElastic()));
                    }
                    return Mono.fromCallable(() -> {
                        dnsRecordRepository.delete(record);
                        return null;
                    }).subscribeOn(Schedulers.boundedElastic());
                })
                .then();
    }

    private Mono<Void> deleteFromCloudflare(Long domainId, DomainDnsRecord record) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));
            if (domain.getDnsProviderId() == null) {
                throw new RuntimeException("No DNS provider configured for this domain.");
            }
            DnsProvider provider = dnsProviderRepository.findById(domain.getDnsProviderId())
                    .orElseThrow(() -> new RuntimeException("DNS provider not found: " + domain.getDnsProviderId()));
            if (provider.getType() != DnsProviderType.CLOUDFLARE) {
                throw new RuntimeException("DNS provider is not Cloudflare.");
            }
            return new ProviderContext(domain.getDomain(), extractApiToken(provider));
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(ctx -> cloudflareApiClient.getZoneId(ctx.domainName(), ctx.apiToken())
                .flatMap(zoneId -> deleteAllMatchingCloudflareRecords(zoneId, record, ctx.apiToken())));
    }

    /**
     * Queries Cloudflare for all records matching the given type and name, then deletes each one.
     * This handles cases where duplicate records exist in Cloudflare (e.g. multiple TXT records
     * with the same name created by successive sync operations) — all of them are removed.
     */
    private Mono<Void> deleteAllMatchingCloudflareRecords(String zoneId, DomainDnsRecord record, String apiToken) {
        return cloudflareApiClient.findDnsRecords(zoneId, record.getRecordType(), record.getName(), apiToken)
                .flatMap(ids -> {
                    if (ids.isEmpty()) {
                        log.warn("No Cloudflare records found for {} {}, nothing to delete remotely",
                                record.getRecordType(), record.getName());
                        return Mono.<Void>empty();
                    }
                    log.info("Deleting {} Cloudflare record(s) for {} {}",
                            ids.size(), record.getRecordType(), record.getName());
                    Mono<Void> chain = Mono.empty();
                    for (String id : ids) {
                        chain = chain.then(cloudflareApiClient.deleteDnsRecord(zoneId, id, apiToken));
                    }
                    return chain;
                });
    }

    private String extractApiToken(DnsProvider provider) {
        try {
            String decrypted = encryptionService.decrypt(provider.getCredentials());
            JsonNode creds = objectMapper.readTree(decrypted);
            return creds.get("apiToken").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read API token from provider credentials", e);
        }
    }

    private record SyncContext(Domain domain, String apiToken, List<DomainDnsRecord> records) {}

    private record ProviderContext(String domainName, String apiToken) {}
}
