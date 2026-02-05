package com.robin.gateway.service;

import com.robin.gateway.model.Alias;
import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.AliasRepository;
import com.robin.gateway.repository.DnsRecordRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.service.DkimService;
import com.robin.gateway.service.DnsRecordGenerator;
import com.robin.gateway.service.ConfigurationService;
import com.robin.gateway.service.dns.DnsProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainService {

    private final DomainRepository domainRepository;
    private final AliasRepository aliasRepository;
    private final com.robin.gateway.repository.ProviderConfigRepository providerConfigRepository;
    private final DnsRecordRepository dnsRecordRepository;
    private final DkimService dkimService;
    private final DnsRecordGenerator dnsRecordGenerator;
    private final ConfigurationService configService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private final DnsProviderFactory dnsProviderFactory;

    /**
     * Get DNSSEC status and DS records
     */
    public Mono<List<DnsRecord>> getDnssecStatus(Long domainId) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            if (domain.getDnsProviderType() == Domain.DnsProviderType.MANUAL) {
                // For manual, we can only return what we have locally or nothing
                return List.<DnsRecord>of();
            }

            com.robin.gateway.service.dns.DnsProvider provider = dnsProviderFactory.getProvider(domain.getDnsProviderType());
            return provider.getDsRecords(domain);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Enable DNSSEC
     */
    public Mono<Void> enableDnssec(Long domainId) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            if (domain.getDnsProviderType() != Domain.DnsProviderType.MANUAL) {
                com.robin.gateway.service.dns.DnsProvider provider = dnsProviderFactory.getProvider(domain.getDnsProviderType());
                provider.enableDnssec(domain);
            }
            
            domain.setDnssecEnabled(true);
            domainRepository.save(domain);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Disable DNSSEC
     */
    public Mono<Void> disableDnssec(Long domainId) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            if (domain.getDnsProviderType() != Domain.DnsProviderType.MANUAL) {
                com.robin.gateway.service.dns.DnsProvider provider = dnsProviderFactory.getProvider(domain.getDnsProviderType());
                provider.disableDnssec(domain);
            }

            domain.setDnssecEnabled(false);
            domainRepository.save(domain);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Get all domains with pagination
     */
    public Mono<Page<Domain>> getAllDomains(Pageable pageable) {
        return Mono.fromCallable(() -> {
            log.debug("Fetching domains with pageable: {}", pageable);
            return domainRepository.findAll(pageable);
        })
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
    public Mono<Domain> createDomain(String domainName, Long dnsProviderId, Long registrarProviderId, Long emailProviderId, Domain configOverride, List<com.robin.gateway.controller.DomainController.InitialRecordRequest> initialRecords) {
        return Mono.fromCallable(() -> transactionTemplate.execute(status -> {
            log.info("Starting atomic transaction for domain creation: {}", domainName);
            
            // Check if domain already exists
            if (domainRepository.existsByDomain(domainName)) {
                throw new IllegalArgumentException("Domain already exists: " + domainName);
            }

            Domain.DomainBuilder builder = Domain.builder()
                    .domain(domainName);

            if (dnsProviderId != null) {
                providerConfigRepository.findById(dnsProviderId).ifPresent(p -> {
                    builder.dnsProvider(p);
                    builder.dnsProviderType(Domain.DnsProviderType.valueOf(p.getType().name()));
                });
            }

            if (registrarProviderId != null) {
                providerConfigRepository.findById(registrarProviderId).ifPresent(p -> {
                    builder.registrarProvider(p);
                    builder.registrarProviderType(Domain.RegistrarProviderType.valueOf(p.getType().name()));
                });
            }

            if (emailProviderId != null) {
                providerConfigRepository.findById(emailProviderId).ifPresent(builder::emailProvider);
            }

            // Apply global defaults for DMARC/SPF
            try {
                java.util.Map<String, Object> config = configService.getConfig("email_reporting").block();
                if (config != null) {
                    builder.dmarcReportingEmail((String) config.get("reportingEmail"));
                    
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dmarc = (java.util.Map<String, Object>) config.get("dmarc");
                    if (dmarc != null) {
                        builder.dmarcPolicy((String) dmarc.get("policy"));
                        builder.dmarcSubdomainPolicy((String) dmarc.get("subdomainPolicy"));
                        if (dmarc.get("percentage") instanceof Number) {
                            builder.dmarcPercentage(((Number) dmarc.get("percentage")).intValue());
                        }
                        builder.dmarcAlignment((String) dmarc.get("alignment"));
                    }

                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> spf = (java.util.Map<String, Object>) config.get("spf");
                    if (spf != null) {
                        builder.spfIncludes((String) spf.get("includes"));
                        if (spf.get("softFail") instanceof Boolean) {
                            builder.spfSoftFail((Boolean) spf.get("softFail"));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load global defaults for new domain", e);
            }

            // Apply overrides if present (from discovery/user input)
            if (configOverride != null) {
                if (configOverride.getDmarcPolicy() != null) builder.dmarcPolicy(configOverride.getDmarcPolicy());
                if (configOverride.getDmarcSubdomainPolicy() != null) builder.dmarcSubdomainPolicy(configOverride.getDmarcSubdomainPolicy());
                if (configOverride.getDmarcPercentage() != null) builder.dmarcPercentage(configOverride.getDmarcPercentage());
                if (configOverride.getDmarcAlignment() != null) builder.dmarcAlignment(configOverride.getDmarcAlignment());
                if (configOverride.getDmarcReportingEmail() != null) builder.dmarcReportingEmail(configOverride.getDmarcReportingEmail());
                
                if (configOverride.getSpfIncludes() != null) builder.spfIncludes(configOverride.getSpfIncludes());
                if (configOverride.getSpfSoftFail() != null) builder.spfSoftFail(configOverride.getSpfSoftFail());
            }

            Domain domain = builder.build();
            domain = domainRepository.save(domain);

            // Save records
            if (initialRecords != null && !initialRecords.isEmpty()) {
                final Domain savedDomain = domain;
                List<DnsRecord> records = initialRecords.stream().map(r -> DnsRecord.builder()
                        .domain(savedDomain)
                        .type(r.getType())
                        .name(r.getName())
                        .content(r.getContent())
                        .ttl(r.getTtl())
                        .priority(r.getPriority())
                        .purpose(r.getPurpose() != null ? r.getPurpose() : DnsRecord.RecordPurpose.OTHER)
                        .syncStatus(DnsRecord.SyncStatus.PENDING)
                        .build()).toList();
                dnsRecordRepository.saveAll(records);
                log.info("Saved {} initial DNS records for domain {}", records.size(), domain.getDomain());
            } else {
                // Default flow: Generate expected DNS records
                List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);
                dnsRecordRepository.saveAll(records);
            }

            return domain;
        }))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(d -> log.info("Created domain: {}", d.getDomain()))
                .doOnError(e -> log.error("Error creating domain: {}", domainName, e));
    }

    /**
     * Update an existing domain
     */
    @Transactional
    public Mono<Domain> updateDomain(Long id, Domain update) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + id));

            if (update.getDnsProviderType() != null) domain.setDnsProviderType(update.getDnsProviderType());
            if (update.getRegistrarProviderType() != null) domain.setRegistrarProviderType(update.getRegistrarProviderType());
            
            if (update.getDnsProvider() != null && update.getDnsProvider().getId() != null) {
                providerConfigRepository.findById(update.getDnsProvider().getId()).ifPresent(domain::setDnsProvider);
            } else if (update.getDnsProvider() == null) {
                domain.setDnsProvider(null);
            }

            if (update.getRegistrarProvider() != null && update.getRegistrarProvider().getId() != null) {
                providerConfigRepository.findById(update.getRegistrarProvider().getId()).ifPresent(domain::setRegistrarProvider);
            } else if (update.getRegistrarProvider() == null) {
                domain.setRegistrarProvider(null);
            }

            // Other fields
            if (update.getDnssecEnabled() != null) domain.setDnssecEnabled(update.getDnssecEnabled());
            if (update.getMtaStsEnabled() != null) domain.setMtaStsEnabled(update.getMtaStsEnabled());
            if (update.getMtaStsMode() != null) domain.setMtaStsMode(update.getMtaStsMode());
            if (update.getDaneEnabled() != null) domain.setDaneEnabled(update.getDaneEnabled());
            if (update.getBimiSelector() != null) domain.setBimiSelector(update.getBimiSelector());
            if (update.getBimiLogoUrl() != null) domain.setBimiLogoUrl(update.getBimiLogoUrl());

            // DMARC & SPF
            if (update.getDmarcPolicy() != null) domain.setDmarcPolicy(update.getDmarcPolicy());
            if (update.getDmarcSubdomainPolicy() != null) domain.setDmarcSubdomainPolicy(update.getDmarcSubdomainPolicy());
            if (update.getDmarcPercentage() != null) domain.setDmarcPercentage(update.getDmarcPercentage());
            if (update.getDmarcAlignment() != null) domain.setDmarcAlignment(update.getDmarcAlignment());
            if (update.getDmarcReportingEmail() != null) domain.setDmarcReportingEmail(update.getDmarcReportingEmail());
            if (update.getSpfIncludes() != null) domain.setSpfIncludes(update.getSpfIncludes());
            if (update.getSpfSoftFail() != null) domain.setSpfSoftFail(update.getSpfSoftFail());

            return domainRepository.save(domain);
        })
                .subscribeOn(Schedulers.boundedElastic());
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
}
