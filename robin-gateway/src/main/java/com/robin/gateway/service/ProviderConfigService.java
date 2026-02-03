package com.robin.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.model.ProviderConfig;
import com.robin.gateway.repository.ProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderConfigService {

    private final ProviderConfigRepository providerConfigRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public Mono<Page<ProviderConfig>> getAllProviders(Pageable pageable) {
        return Mono.fromCallable(() -> providerConfigRepository.findAll(pageable))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ProviderConfig> getProvider(Long id) {
        return Mono.fromCallable(() -> providerConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider not found: " + id)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<ProviderConfig> createProvider(String name, ProviderConfig.ProviderType type, Map<String, String> credentials) {
        return Mono.fromCallable(() -> {
            String jsonCredentials;
            try {
                jsonCredentials = objectMapper.writeValueAsString(credentials);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Invalid credentials format", e);
            }

            ProviderConfig config = ProviderConfig.builder()
                    .name(name)
                    .type(type)
                    .credentials(encryptionService.encrypt(jsonCredentials))
                    .build();

            return providerConfigRepository.save(config);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(p -> log.info("Created provider: {}", p.getName()));
    }

    @Transactional
    public Mono<Void> deleteProvider(Long id) {
        return Mono.fromCallable(() -> {
            providerConfigRepository.deleteById(id);
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
