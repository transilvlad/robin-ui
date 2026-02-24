package com.robin.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.dto.DnsProviderRequest;
import com.robin.gateway.repository.DnsProviderRepository;
import com.robin.gateway.repository.DomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DnsProviderService {

    private final DnsProviderRepository dnsProviderRepository;
    private final DomainRepository domainRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public Mono<List<DnsProvider>> getAllProviders() {
        return Mono.fromCallable(() -> {
            List<DnsProvider> providers = dnsProviderRepository.findAll();
            providers.forEach(this::maskCredentials);
            return providers;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(providers -> log.debug("Retrieved {} DNS providers", providers.size()))
                .doOnError(e -> log.error("Error retrieving DNS providers", e));
    }

    public Mono<DnsProvider> getProviderById(Long id) {
        return Mono.fromCallable(() -> {
            DnsProvider provider = dnsProviderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("DNS provider not found: " + id));
            maskCredentials(provider);
            return provider;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(p -> log.debug("Retrieved DNS provider: {}", p.getName()))
                .doOnError(e -> log.error("Error retrieving DNS provider with id: {}", id, e));
    }

    @Transactional
    public Mono<DnsProvider> createProvider(DnsProviderRequest request) {
        return Mono.fromCallable(() -> {
            String credentialsJson = serializeCredentials(request);
            String encryptedCredentials = encryptionService.encrypt(credentialsJson);

            DnsProvider provider = DnsProvider.builder()
                    .name(request.getName())
                    .type(request.getType())
                    .credentials(encryptedCredentials)
                    .build();

            DnsProvider saved = dnsProviderRepository.save(provider);
            log.info("Created DNS provider: {}", saved.getName());
            maskCredentials(saved);
            return saved;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error creating DNS provider: {}", request.getName(), e));
    }

    @Transactional
    public Mono<DnsProvider> updateProvider(Long id, DnsProviderRequest request) {
        return Mono.fromCallable(() -> {
            DnsProvider provider = dnsProviderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("DNS provider not found: " + id));

            String credentialsJson = serializeCredentials(request);
            String encryptedCredentials = encryptionService.encrypt(credentialsJson);

            provider.setName(request.getName());
            provider.setType(request.getType());
            provider.setCredentials(encryptedCredentials);

            DnsProvider saved = dnsProviderRepository.save(provider);
            log.info("Updated DNS provider: {}", saved.getName());
            maskCredentials(saved);
            return saved;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error updating DNS provider with id: {}", id, e));
    }

    @Transactional
    public Mono<Void> deleteProvider(Long id) {
        return Mono.fromCallable(() -> {
            if (!dnsProviderRepository.existsById(id)) {
                throw new RuntimeException("DNS provider not found: " + id);
            }

            boolean usedByDomain = domainRepository.findAll().stream()
                    .anyMatch(d -> id.equals(d.getDnsProviderId()) || id.equals(d.getNsProviderId()));

            if (usedByDomain) {
                throw new IllegalStateException("DNS provider is in use by one or more domains and cannot be deleted");
            }

            dnsProviderRepository.deleteById(id);
            log.info("Deleted DNS provider with id: {}", id);
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .doOnError(e -> log.error("Error deleting DNS provider with id: {}", id, e));
    }

    public Mono<Boolean> testConnection(Long id) {
        return Mono.fromCallable(() -> {
            DnsProvider provider = dnsProviderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("DNS provider not found: " + id));
            log.info("Testing connection for DNS provider: {} (type={}). Actual API call pending implementation.",
                    provider.getName(), provider.getType());
            return true;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error testing connection for DNS provider with id: {}", id, e));
    }

    private String serializeCredentials(DnsProviderRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getCredentials());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize credentials", e);
        }
    }

    private void maskCredentials(DnsProvider provider) {
        provider.setCredentials("****");
    }
}
