package com.robin.gateway.service;

import com.robin.gateway.model.Alias;
import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.AliasRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.model.dto.DomainRequest;
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
    private final MtaStsService mtaStsService;

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

            return domainRepository.save(domain);
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
}
