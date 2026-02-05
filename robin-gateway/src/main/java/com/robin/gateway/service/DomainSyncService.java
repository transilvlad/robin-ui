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
import java.util.Optional;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainSyncService {

    private final DomainRepository domainRepository;
    private final DnsRecordRepository dnsRecordRepository;
    private final DnsRecordGenerator dnsRecordGenerator;
    private final DnsProviderFactory dnsProviderFactory;
    private final PlatformTransactionManager transactionManager;

    public Mono<Void> syncDomain(Long domainId) {
        return Mono.fromCallable(() -> {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            
            return transactionTemplate.execute(status -> {
                Domain domain = domainRepository.findById(domainId)
                        .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

                log.info("Starting DNS sync for domain: {}", domain.getDomain());

                // 1. Get current state from DB
                List<DnsRecord> localRecords = dnsRecordRepository.findByDomain_Id(domainId);
                
                // 2. Sync to external provider if not MANUAL
                if (domain.getDnsProviderType() != Domain.DnsProviderType.MANUAL) {
                    DnsProvider provider = dnsProviderFactory.getProvider(domain.getDnsProviderType());
                    
                    try {
                        // Fetch provider's current state
                        List<DnsRecord> remoteRecords = provider.listRecords(domain);
                        
                        // A. Handle Creates and Updates
                        for (DnsRecord local : localRecords) {
                            Optional<DnsRecord> remoteMatch = findRemoteMatch(remoteRecords, local, domain.getDomain());

                            if (remoteMatch.isPresent()) {
                                DnsRecord remote = remoteMatch.get();
                                local.setExternalId(remote.getExternalId());
                                
                                if (needsUpdate(local, remote)) {
                                    log.info("Updating remote record: {} {}", local.getType(), local.getName());
                                    provider.updateRecord(domain, local);
                                }
                            } else {
                                log.info("Creating missing remote record: {} {}", local.getType(), local.getName());
                                provider.createRecord(domain, local);
                            }
                            
                            local.setSyncStatus(DnsRecord.SyncStatus.SYNCED);
                            local.setLastSyncedAt(java.time.LocalDateTime.now());
                        }

                        // B. Handle Deletions (Remove from remote if missing from local)
                        // STRICT SYNC: Robin is the Source of Truth for MANAGED records.
                        for (DnsRecord remote : remoteRecords) {
                            if (!isRobinManaged(remote, domain.getDomain())) {
                                continue; // Don't touch records Robin doesn't care about
                            }

                            boolean inLocal = localRecords.stream().anyMatch(l -> 
                                findRemoteMatch(List.of(remote), l, domain.getDomain()).isPresent());
                            
                            if (!inLocal) {
                                log.info("Deleting managed remote record not present in Robin: {} {}", remote.getType(), remote.getName());
                                provider.deleteRecord(domain, remote.getExternalId());
                            }
                        }
                        
                        // Final save to update IDs and sync status for existing managed records
                        dnsRecordRepository.saveAll(localRecords);
                        
                    } catch (Exception e) {
                        log.error("Provider sync failed for domain {}", domain.getDomain(), e);
                        throw new RuntimeException("Provider sync failed: " + e.getMessage(), e);
                    }
                }
                return null;
            });
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Optional<DnsRecord> findRemoteMatch(List<DnsRecord> remoteRecords, DnsRecord local, String domainName) {
        return remoteRecords.stream()
                .filter(r -> {
                    // 1. Try strict match by External ID if available
                    if (local.getExternalId() != null) {
                        return local.getExternalId().equals(r.getExternalId());
                    }
                    
                    // 2. Filter by basic properties
                    if (r.getType() != local.getType()) return false;
                    
                    String rName = r.getName();
                    String lName = local.getName();
                    
                    boolean nameMatch;
                    if ("@".equals(lName)) {
                        nameMatch = rName.equals(domainName) || rName.equals(domainName + ".");
                    } else {
                        nameMatch = rName.equals(lName) || 
                                    rName.equals(lName + "." + domainName) ||
                                    rName.equals(lName + "." + domainName + ".");
                    }
                    
                    if (!nameMatch) return false;

                    // 3. For Root A/AAAA records, we match by name+type only to handle proxied IPs
                    if (("@".equals(lName) || domainName.equals(lName)) && 
                        (local.getType() == DnsRecord.RecordType.A || local.getType() == DnsRecord.RecordType.AAAA)) {
                        return true;
                    }

                    // 4. If no External ID, check Content for exact match
                    return needsUpdate(local, r) == false; // content match
                })
                .findFirst();
    }

    private boolean needsUpdate(DnsRecord local, DnsRecord remote) {
        String localContent = local.getContent();
        String remoteContent = remote.getContent();
        
        boolean contentMatches = localContent.equals(remoteContent);
        // Quote-insensitive comparison for TXT
        if (!contentMatches && local.getType() == DnsRecord.RecordType.TXT) {
            String unquotedLocal = localContent.replaceAll("^\"|\"$", "");
            String unquotedRemote = remoteContent.replaceAll("^\"|\"$", "");
            contentMatches = unquotedLocal.equals(unquotedRemote);
        }

        return !contentMatches || 
               (local.getPriority() != null && !local.getPriority().equals(remote.getPriority()));
    }

    private boolean isRobinManaged(DnsRecord remote, String domainName) {
        String name = remote.getName();
        DnsRecord.RecordType type = remote.getType();
        String content = remote.getContent() != null ? remote.getContent() : "";

        // Critical email host
        if (name.equals("mail") || name.equals("mail." + domainName)) return true;
        
        // Root records for email
        if (name.equals("@") || name.equals(domainName)) {
            if (type == DnsRecord.RecordType.MX) return true;
            if (type == DnsRecord.RecordType.TXT && content.contains("v=spf1")) return true;
        }

        // Security / Meta records
        if (name.startsWith("_dmarc")) return true;
        if (name.contains("_domainkey")) return true;
        if (name.startsWith("mta-sts") || name.startsWith("_mta-sts")) return true;
        if (name.startsWith("_25._tcp.mail")) return true;
        if (name.startsWith("autoconfig") || name.startsWith("autodiscover")) return true;
        
        return false;
    }
}
