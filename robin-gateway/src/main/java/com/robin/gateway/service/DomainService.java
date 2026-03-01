package com.robin.gateway.service;

import com.robin.gateway.model.Alias;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.DnsProviderType;
import com.robin.gateway.model.DomainDnsRecord;
import com.robin.gateway.repository.AliasRepository;
import com.robin.gateway.repository.DnsProviderRepository;
import com.robin.gateway.repository.DomainDnsRecordRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.repository.DkimDetectedSelectorRepository;
import com.robin.gateway.model.DkimDetectedSelector;
import com.robin.gateway.model.dto.DomainLookupResult;
import com.robin.gateway.model.dto.DomainLookupResult.DetectedDkimSelector;
import com.robin.gateway.model.dto.DomainRequest;
import com.robin.gateway.model.dto.DomainSummary;
import com.robin.gateway.repository.DomainHealthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainService {

    private final DomainRepository domainRepository;
    private final AliasRepository aliasRepository;
    private final MtaStsService mtaStsService;
    private final DomainHealthRepository domainHealthRepository;
    private final DnsProviderRepository dnsProviderRepository;
    private final DomainDnsRecordRepository domainDnsRecordRepository;
    private final DnsResolverService dnsResolverService;
    private final DkimDetectedSelectorRepository dkimDetectedSelectorRepository;

    /**
     * Get all domains with pagination
     */
    public Mono<Page<Domain>> getAllDomains(Pageable pageable) {
        return Mono.fromCallable(() -> domainRepository.findAll(pageable))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(domains -> log.debug("Retrieved {} domains", domains.getTotalElements()))
                .doOnError(e -> log.error("Error retrieving domains", e));
    }

    /**
     * Get domain by ID
     */
    public Mono<Domain> getDomainById(Long id) {
        return Mono.fromCallable(() -> domainRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalDomain -> optionalDomain
                        .map(Mono::just)
                        .orElse(Mono.error(new RuntimeException("Domain not found: " + id))))
                .doOnSuccess(domain -> log.debug("Retrieved domain: {}", domain.getDomain()))
                .doOnError(e -> log.error("Error retrieving domain with id: {}", id, e));
    }

    /**
     * Get domain summary by ID
     */
    public Mono<DomainSummary> getDomainSummary(Long id) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + id));
            return DomainSummary.builder()
                    .domain(domain)
                    .healthChecks(domainHealthRepository.findByDomainId(id))
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Get domain by name
     */
    public Mono<Optional<Domain>> getDomainByName(String domainName) {
        return Mono.fromCallable(() -> domainRepository.findByDomain(domainName))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(domain -> log.debug("Retrieved domain by name: {}", domainName))
                .doOnError(e -> log.error("Error retrieving domain by name: {}", domainName, e));
    }

    /**
     * Create a new domain
     */
    @Transactional
    public Mono<Domain> createDomain(DomainRequest request) {
        return Mono.fromCallable(() -> {
            String domainName = request.getDomain();
            // Check if domain already exists
            if (domainRepository.existsByDomain(domainName)) {
                throw new IllegalArgumentException("Domain already exists: " + domainName);
            }

            Domain domain = Domain.builder()
                    .domain(domainName)
                    .dnsProviderId(request.getDnsProviderId())
                    .nsProviderId(request.getNsProviderId())
                    .status(request.isExistingDomain() ? "PENDING_VERIFICATION" : "PENDING")
                    .build();

            Domain saved = domainRepository.save(domain);

            // Persist pre-flight DNS records as an unmanaged snapshot
            if (request.getInitialDnsRecords() != null && !request.getInitialDnsRecords().isEmpty()) {
                List<DomainDnsRecord> records = request.getInitialDnsRecords().stream()
                        .map(r -> DomainDnsRecord.builder()
                                .domainId(saved.getId())
                                .recordType(r.getRecordType())
                                .name(r.getName())
                                .value(r.getValue())
                                .priority(r.getPriority())
                                .ttl(r.getTtl())
                                .managed(false)
                                .build())
                        .toList();
                domainDnsRecordRepository.saveAll(records);
                log.info("Saved {} initial DNS records for domain {}", records.size(), domainName);
            }

            return saved;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(domain -> {
                    log.info("Created domain: {}", domain.getDomain());
                    if (domain.getDnsProviderId() != null) {
                        return mtaStsService.initiateWorkerDeployment(domain.getId())
                                .thenReturn(domain)
                                .onErrorResume(e -> {
                                    log.error("Failed to trigger MTA-STS worker deployment for domain {}", domain.getId(), e);
                                    return Mono.just(domain);
                                });
                    }
                    return Mono.just(domain);
                })
                .doOnError(e -> log.error("Error creating domain: {}", request.getDomain(), e));
    }

    /**
     * Delete domain by ID
     */
    @Transactional
    public Mono<Void> deleteDomain(Long id) {
        return Mono.fromCallable(() -> {
            if (!domainRepository.existsById(id)) {
                throw new RuntimeException("Domain not found: " + id);
            }

            // Delete all aliases for this domain first
            Domain domain = domainRepository.findById(id).orElseThrow();
            List<Alias> aliases = aliasRepository.findBySource(domain.getDomain() + "%");
            if (!aliases.isEmpty()) {
                aliasRepository.deleteAll(aliases);
                log.info("Deleted {} aliases for domain {}", aliases.size(), domain.getDomain());
            }

            domainRepository.deleteById(id);
            log.info("Deleted domain with id: {}", id);
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .doOnError(e -> log.error("Error deleting domain with id: {}", id, e));
    }

    // ===== Alias Management =====

    /**
     * Get all aliases for a domain
     */
    public Mono<List<Alias>> getAliasesByDomain(Long domainId) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            return aliasRepository.findBySource(domain.getDomain() + "%");
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(aliases -> log.debug("Retrieved {} aliases for domain id: {}", aliases.size(), domainId))
                .doOnError(e -> log.error("Error retrieving aliases for domain id: {}", domainId, e));
    }

    /**
     * Get all aliases with pagination
     */
    public Mono<Page<Alias>> getAllAliases(Pageable pageable) {
        return Mono.fromCallable(() -> aliasRepository.findAll(pageable))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(aliases -> log.debug("Retrieved {} aliases", aliases.getTotalElements()))
                .doOnError(e -> log.error("Error retrieving aliases", e));
    }

    /**
     * Get alias by ID
     */
    public Mono<Alias> getAliasById(Long id) {
        return Mono.fromCallable(() -> aliasRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalAlias -> optionalAlias
                        .map(Mono::just)
                        .orElse(Mono.error(new RuntimeException("Alias not found: " + id))))
                .doOnSuccess(alias -> log.debug("Retrieved alias: {} -> {}", alias.getSource(), alias.getDestination()))
                .doOnError(e -> log.error("Error retrieving alias with id: {}", id, e));
    }

    /**
     * Create a new alias
     */
    @Transactional
    public Mono<Alias> createAlias(String source, String destination) {
        return Mono.fromCallable(() -> {
            // Validate email format
            if (!source.contains("@") || !destination.contains("@")) {
                throw new IllegalArgumentException("Invalid email format for alias");
            }

            // Extract domain from source email
            String sourceDomain = source.substring(source.indexOf("@") + 1);

            // Check if domain exists
            if (!domainRepository.existsByDomain(sourceDomain)) {
                throw new IllegalArgumentException("Source domain does not exist: " + sourceDomain);
            }

            // Check if alias already exists
            List<Alias> existingAliases = aliasRepository.findBySource(source);
            if (!existingAliases.isEmpty()) {
                throw new IllegalArgumentException("Alias already exists: " + source);
            }

            Alias alias = Alias.builder()
                    .source(source)
                    .destination(destination)
                    .build();

            return aliasRepository.save(alias);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(alias -> log.info("Created alias: {} -> {}", alias.getSource(), alias.getDestination()))
                .doOnError(e -> log.error("Error creating alias: {} -> {}", source, destination, e));
    }

    /**
     * Update an existing alias
     */
    @Transactional
    public Mono<Alias> updateAlias(Long id, String destination) {
        return Mono.fromCallable(() -> {
            Alias alias = aliasRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Alias not found: " + id));

            if (!destination.contains("@")) {
                throw new IllegalArgumentException("Invalid email format for destination");
            }

            alias.setDestination(destination);
            return aliasRepository.save(alias);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(alias -> log.info("Updated alias: {} -> {}", alias.getSource(), alias.getDestination()))
                .doOnError(e -> log.error("Error updating alias with id: {}", id, e));
    }

    /**
     * Delete alias by ID
     */
    @Transactional
    public Mono<Void> deleteAlias(Long id) {
        return Mono.fromCallable(() -> {
            if (!aliasRepository.existsById(id)) {
                throw new RuntimeException("Alias not found: " + id);
            }

            aliasRepository.deleteById(id);
            log.info("Deleted alias with id: {}", id);
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .doOnError(e -> log.error("Error deleting alias with id: {}", id, e));
    }

    // ===== DNS Pre-flight Lookup =====

    /**
     * Look up existing DNS records for a domain and suggest a DNS provider
     * based on the detected nameserver vendor.
     */
    public Mono<DomainLookupResult> lookupDomain(String domain) {
        return Mono.fromCallable(() -> {
            log.info("Performing DNS lookup for domain: {}", domain);

            List<DomainLookupResult.DnsRecordEntry> allRecords = new ArrayList<>();

            // NS records (apex)
            List<String> nsRecords = dnsResolverService.resolveNsRecords(domain);
            nsRecords.forEach(v -> allRecords.add(new DomainLookupResult.DnsRecordEntry("NS", domain, v)));

            // A records (apex and mail subdomain)
            dnsResolverService.resolveARecords(domain)
                    .forEach(v -> allRecords.add(new DomainLookupResult.DnsRecordEntry("A", domain, v)));
            dnsResolverService.resolveARecords("mail." + domain)
                    .forEach(v -> allRecords.add(new DomainLookupResult.DnsRecordEntry("A", "mail." + domain, v)));

            // MX records (apex)
            List<String> mxRecords = dnsResolverService.resolveMxRecords(domain);
            mxRecords.forEach(v -> allRecords.add(new DomainLookupResult.DnsRecordEntry("MX", domain, v)));

            // TXT records – apex (ALL, not just SPF)
            List<String> allTxt = dnsResolverService.resolveTxtRecords(domain);
            allTxt.forEach(v -> allRecords.add(new DomainLookupResult.DnsRecordEntry("TXT", domain, v)));

            // TXT records – well-known email subdomains
            List<String> dmarcRecords = dnsResolverService.resolveTxtRecords("_dmarc." + domain);
            dmarcRecords.forEach(v -> allRecords.add(new DomainLookupResult.DnsRecordEntry("TXT", "_dmarc." + domain, v)));

            List<String> mtaSts = dnsResolverService.resolveTxtRecords("_mta-sts." + domain);
            mtaSts.forEach(v -> allRecords.add(new DomainLookupResult.DnsRecordEntry("TXT", "_mta-sts." + domain, v)));

            List<String> smtpTls = dnsResolverService.resolveTxtRecords("_smtp._tls." + domain);
            smtpTls.forEach(v -> allRecords.add(new DomainLookupResult.DnsRecordEntry("TXT", "_smtp._tls." + domain, v)));

            // DKIM – Phase 1: probe a broad set of common selectors
            List<String> dkimBaseSelectors = List.of(
                "default", "google", "mail", "selector1", "selector2",
                "k1", "k2", "k3", "dkim", "dkim1", "dkim2",
                "smtp", "s1", "s2", "key1", "key2",
                "mta", "mta1", "mta2",
                "robin", "robin1", "robin2", "robin3", "robin4", "robin5",
                "email", "outbound", "primary", "main", "a", "b"
            );
            Set<String> foundDkimPrefixes = new java.util.HashSet<>();
            List<DetectedDkimSelector> detectedDkimSelectors = new ArrayList<>();
            List<DkimDetectedSelector> entitiesToSave = new ArrayList<>();

            for (String selector : dkimBaseSelectors) {
                String dkimHost = selector + "._domainkey." + domain;
                List<String> vals = dnsResolverService.resolveTxtRecords(dkimHost);
                vals.forEach(v -> {
                    allRecords.add(new DomainLookupResult.DnsRecordEntry("TXT", dkimHost, v));
                    processDkimRecord(domain, selector, v, detectedDkimSelectors, entitiesToSave);
                });
                if (!vals.isEmpty()) {
                    // Record non-numeric prefix so we can probe further in phase 2
                    foundDkimPrefixes.add(selector.replaceAll("\\d+$", ""));
                }
            }
            // DKIM – Phase 2: for every found prefix, probe up to index 20
            for (String prefix : foundDkimPrefixes) {
                for (int i = 1; i <= 20; i++) {
                    String selector = prefix + i;
                    if (dkimBaseSelectors.contains(selector)) continue; // already checked
                    String dkimHost = selector + "._domainkey." + domain;
                    List<String> vals = dnsResolverService.resolveTxtRecords(dkimHost);
                    if (vals.isEmpty()) break; // stop probing this prefix once gap found
                    vals.forEach(v -> {
                        allRecords.add(new DomainLookupResult.DnsRecordEntry("TXT", dkimHost, v));
                        processDkimRecord(domain, selector, v, detectedDkimSelectors, entitiesToSave);
                    });
                }
            }
            
            // Save DKIM detected selectors into the repository
            if (!entitiesToSave.isEmpty()) {
                // Upsert logic
                for (DkimDetectedSelector entity : entitiesToSave) {
                    try {
                        Optional<DkimDetectedSelector> existingOpt = dkimDetectedSelectorRepository.findByDomainOrderBySelectorAsc(domain).stream().filter(e -> e.getSelector().equals(entity.getSelector())).findFirst();
                        if (existingOpt.isPresent()) {
                            DkimDetectedSelector existing = existingOpt.get();
                            existing.setPublicKeyDns(entity.getPublicKeyDns());
                            existing.setAlgorithm(entity.getAlgorithm());
                            existing.setTestMode(entity.getTestMode());
                            existing.setRevoked(entity.isRevoked());
                            existing.setDetectedAt(LocalDateTime.now());
                            dkimDetectedSelectorRepository.save(existing);
                        } else {
                            dkimDetectedSelectorRepository.save(entity);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to save detected DKIM selector for {}: {}", domain, e.getMessage());
                    }
                }
            }

            // CNAME – common email-related subdomains
            for (String sub : List.of("autoconfig", "autodiscover", "_mta-sts", "mail", "smtp", "imap", "pop", "webmail")) {
                String host = sub + "." + domain;
                dnsResolverService.resolveCnameRecords(host)
                        .forEach(v -> allRecords.add(new DomainLookupResult.DnsRecordEntry("CNAME", host, v)));
            }

            // Derive SPF/DMARC sub-lists (still used for provider detection)
            List<String> spfRecords = allTxt.stream().filter(v -> v.startsWith("v=spf1")).toList();

            String detectedType = detectNsProviderType(nsRecords);

            List<DnsProvider> allProviders = dnsProviderRepository.findAll();
            allProviders.forEach(p -> p.setCredentials(null));

            DnsProvider suggested = null;
            if (!"UNKNOWN".equals(detectedType)) {
                try {
                    DnsProviderType provType = DnsProviderType.valueOf(detectedType);
                    suggested = allProviders.stream()
                            .filter(p -> p.getType() == provType)
                            .findFirst()
                            .orElse(null);
                } catch (IllegalArgumentException ignored) {}
            }

            return DomainLookupResult.builder()
                    .domain(domain)
                    .nsRecords(nsRecords)
                    .mxRecords(mxRecords)
                    .spfRecords(spfRecords)
                    .dmarcRecords(dmarcRecords)
                    .mtaStsRecords(mtaSts)
                    .smtpTlsRecords(smtpTls)
                    .detectedNsProviderType(detectedType)
                    .suggestedProvider(suggested)
                    .availableProviders(allProviders)
                    .allRecords(allRecords)
                    .detectedDkimSelectors(detectedDkimSelectors)
                    .build();
        }).subscribeOn(Schedulers.boundedElastic())
          .doOnError(e -> log.error("DNS lookup failed for domain: {}", domain, e));
    }

    private void processDkimRecord(String domain, String selector, String rdata, List<DetectedDkimSelector> dtoList, List<DkimDetectedSelector> entities) {
        Map<String, String> tags = new HashMap<>();
        for (String part : rdata.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                tags.put(part.substring(0, eq).trim().toLowerCase(), part.substring(eq + 1).trim());
            }
        }
        
        if (!tags.containsKey("p")) {
            return;
        }
        
        String p = tags.get("p");
        String algorithm = tags.getOrDefault("k", "rsa");
        boolean testMode = tags.containsKey("t") && tags.get("t").contains("y");
        boolean revoked = p.isEmpty();
        
        // Add to DTO
        String preview = p.length() > 20 ? p.substring(0, 20) + "..." : p;
        dtoList.add(DetectedDkimSelector.builder()
                .selector(selector)
                .algorithm(algorithm)
                .publicKeyPreview(preview)
                .testMode(testMode)
                .revoked(revoked)
                .detectedAt(LocalDateTime.now().toString())
                .build());
                
        // Add to Entity
        entities.add(DkimDetectedSelector.builder()
                .domain(domain)
                .selector(selector)
                .publicKeyDns(p)
                .algorithm(algorithm)
                .testMode(testMode)
                .revoked(revoked)
                .build());
    }

    private String detectNsProviderType(List<String> nsRecords) {
        for (String ns : nsRecords) {
            String lower = ns.toLowerCase();
            if (lower.contains(".ns.cloudflare.com")) {
                return "CLOUDFLARE";
            }
            if (lower.contains(".awsdns-")) {
                return "AWS_ROUTE53";
            }
        }
        return "UNKNOWN";
    }
}
