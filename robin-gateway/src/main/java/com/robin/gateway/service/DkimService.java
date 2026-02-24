package com.robin.gateway.service;

import com.robin.gateway.model.DkimAlgorithm;
import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.DkimKeyStatus;
import com.robin.gateway.repository.DkimKeyRepository;
import com.robin.gateway.repository.DomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DkimService {

    private final DkimKeyRepository dkimKeyRepository;
    private final DomainRepository domainRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public Mono<DkimKey> generateKeyPair(Long domainId, DkimAlgorithm algorithm, String selectorOverride) {
        return Mono.fromCallable(() -> {
            if (!domainRepository.existsById(domainId)) {
                throw new RuntimeException("Domain not found: " + domainId);
            }

            String selector = selectorOverride != null ? selectorOverride : buildAutoSelector(algorithm);

            KeyPair keyPair = generateKeyPairForAlgorithm(algorithm);

            String privateKeyEncoded = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            String publicKeyEncoded = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String encryptedPrivateKey = encryptionService.encrypt(privateKeyEncoded);

            DkimKey dkimKey = DkimKey.builder()
                    .domainId(domainId)
                    .selector(selector)
                    .algorithm(algorithm)
                    .privateKey(encryptedPrivateKey)
                    .publicKey(publicKeyEncoded)
                    .status(DkimKeyStatus.ACTIVE)
                    .build();

            DkimKey saved = dkimKeyRepository.save(dkimKey);
            log.info("Generated DKIM key for domain {} with selector '{}' and algorithm {}", domainId, selector, algorithm);

            DkimKey masked = copyWithMaskedPrivateKey(saved);
            return masked;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error generating DKIM key for domain: {}", domainId, e));
    }

    @Transactional
    public Mono<DkimKey> initiateRotation(Long domainId) {
        return Mono.fromCallable(() -> {
            List<DkimKey> activeKeys = dkimKeyRepository.findByDomainIdAndStatus(domainId, DkimKeyStatus.ACTIVE);
            if (activeKeys.isEmpty()) {
                throw new RuntimeException("No active DKIM key found for domain: " + domainId);
            }

            DkimKey currentKey = activeKeys.get(0);
            String oldSelector = currentKey.getSelector();

            currentKey.setStatus(DkimKeyStatus.ROTATING);
            dkimKeyRepository.save(currentKey);
            log.info("Set DKIM key '{}' to ROTATING for domain {}", oldSelector, domainId);

            return oldSelector;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(oldSelector -> generateKeyPair(domainId, DkimAlgorithm.RSA_2048, null)
                        .flatMap(newKey -> Mono.fromCallable(() -> {
                            DkimKey persisted = dkimKeyRepository.findById(newKey.getId())
                                    .orElseThrow(() -> new RuntimeException("Newly generated key not found"));
                            persisted.setCnameSelector(oldSelector);
                            DkimKey updated = dkimKeyRepository.save(persisted);
                            log.info("DKIM rotation initiated for domain {}; new selector='{}' points back to old selector='{}'",
                                    domainId, updated.getSelector(), oldSelector);
                            return copyWithMaskedPrivateKey(updated);
                        }).subscribeOn(Schedulers.boundedElastic())))
                .doOnError(e -> log.error("Error initiating DKIM rotation for domain: {}", domainId, e));
    }

    @Transactional
    public Mono<Void> retireKey(Long keyId) {
        return Mono.fromCallable(() -> {
            DkimKey key = dkimKeyRepository.findById(keyId)
                    .orElseThrow(() -> new RuntimeException("DKIM key not found: " + keyId));

            key.setStatus(DkimKeyStatus.RETIRED);
            key.setRetiredAt(LocalDateTime.now());
            dkimKeyRepository.save(key);
            log.info("Retired DKIM key {} (selector='{}')", keyId, key.getSelector());
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .doOnError(e -> log.error("Error retiring DKIM key: {}", keyId, e));
    }

    public Mono<List<DkimKey>> getKeysForDomain(Long domainId) {
        return Mono.fromCallable(() -> {
            List<DkimKey> keys = dkimKeyRepository.findByDomainId(domainId);
            return keys.stream()
                    .map(this::copyWithMaskedPrivateKey)
                    .collect(Collectors.toList());
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(keys -> log.debug("Retrieved {} DKIM keys for domain {}", keys.size(), domainId))
                .doOnError(e -> log.error("Error retrieving DKIM keys for domain: {}", domainId, e));
    }

    public Mono<DkimKey> getPublicKeyInfo(Long keyId) {
        return Mono.fromCallable(() -> {
            DkimKey key = dkimKeyRepository.findById(keyId)
                    .orElseThrow(() -> new RuntimeException("DKIM key not found: " + keyId));
            return copyWithMaskedPrivateKey(key);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(k -> log.debug("Retrieved public key info for DKIM key {}", keyId))
                .doOnError(e -> log.error("Error retrieving DKIM key: {}", keyId, e));
    }

    private String buildAutoSelector(DkimAlgorithm algorithm) {
        String yearMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String suffix = (algorithm == DkimAlgorithm.RSA_2048) ? "r" : "e";
        return yearMonth + suffix;
    }

    private KeyPair generateKeyPairForAlgorithm(DkimAlgorithm algorithm) {
        try {
            if (algorithm == DkimAlgorithm.RSA_2048) {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                return keyGen.generateKeyPair();
            } else {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
                return keyGen.generateKeyPair();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate key pair for algorithm: " + algorithm, e);
        }
    }

    private DkimKey copyWithMaskedPrivateKey(DkimKey key) {
        return DkimKey.builder()
                .id(key.getId())
                .domainId(key.getDomainId())
                .selector(key.getSelector())
                .algorithm(key.getAlgorithm())
                .privateKey("****")
                .publicKey(key.getPublicKey())
                .cnameSelector(key.getCnameSelector())
                .status(key.getStatus())
                .createdAt(key.getCreatedAt())
                .retiredAt(key.getRetiredAt())
                .build();
    }
}
