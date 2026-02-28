package com.robin.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.integration.aws.Route53ApiClient;
import com.robin.gateway.integration.cloudflare.CloudflareApiClient;
import com.robin.gateway.model.DkimAlgorithm;
import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.DkimKeyStatus;
import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.DnsProviderType;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.DomainDnsRecord;
import com.robin.gateway.repository.DkimKeyRepository;
import com.robin.gateway.repository.DnsProviderRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.repository.DomainDnsRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;
import software.amazon.awssdk.services.route53.model.RRType;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DkimService {

    private final DkimKeyRepository dkimKeyRepository;
    private final DomainRepository domainRepository;
    private final EncryptionService encryptionService;
    private final DnsProviderRepository dnsProviderRepository;
    private final CloudflareApiClient cloudflareApiClient;
    private final Route53ApiClient route53ApiClient;
    private final DomainDnsRecordRepository dnsRecordRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${robin.service-url:http://localhost:8090}")
    private String robinServiceUrl;

    @Transactional
    public Mono<DkimKey> generateKeyPair(Long domainId, DkimAlgorithm algorithm, String selectorOverride) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

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

            return saved;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(saved -> publishToDns(saved)
                .onErrorResume(e -> {
                    log.warn("DNS publish failed for key {} (domain {}), key saved but DNS record may be missing: {}",
                            saved.getId(), domainId, e.getMessage());
                    return Mono.empty();
                })
                .then(configureMtaSigning(saved))
                .onErrorResume(e -> {
                    log.warn("MTA signing config failed for key {} (domain {}), key saved but MTA may not sign yet: {}",
                            saved.getId(), domainId, e.getMessage());
                    return Mono.empty();
                })
                .thenReturn(saved)
        )
        .map(this::copyWithMaskedPrivateKey)
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
        String rand = Long.toHexString(System.currentTimeMillis()).substring(5);
        return yearMonth + suffix + rand;
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

    public Mono<Void> publishToDns(DkimKey dkimKey) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(dkimKey.getDomainId())
                    .orElseThrow(() -> new RuntimeException("Domain not found"));
            return domain;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(domain -> {
            if (domain.getDnsProviderId() == null) {
                log.info("No DNS provider configured for domain {}, skipping DNS publish", domain.getDomain());
                return Mono.empty();
            }

            return Mono.fromCallable(() -> dnsProviderRepository.findById(domain.getDnsProviderId())
                    .orElseThrow(() -> new RuntimeException("Provider not found")))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(provider -> {
                        String recordName = dkimKey.getSelector() + "._domainkey";
                        String algorithmTag = dkimKey.getAlgorithm() == DkimAlgorithm.RSA_2048 ? "rsa" : "ed25519";
                        String recordValue = "v=DKIM1; k=" + algorithmTag + "; p=" + dkimKey.getPublicKey();
                        
                        String decryptedCreds = encryptionService.decrypt(provider.getCredentials());

                        return doPublishToDns(domain, provider, decryptedCreds, recordName, recordValue);
                    });
        });
    }

    private Mono<Void> doPublishToDns(Domain domain, DnsProvider provider, String credentialsJson, String recordName, String recordValue) {
        try {
            JsonNode creds = objectMapper.readTree(credentialsJson);
            
            if (provider.getType() == DnsProviderType.CLOUDFLARE) {
                String apiToken = creds.get("apiToken").asText();
                return cloudflareApiClient.getZoneId(domain.getDomain(), apiToken)
                        .flatMap(zoneId -> cloudflareApiClient.createDnsRecord(zoneId, "TXT", recordName, recordValue, 1, apiToken))
                        .flatMap(recordId -> saveDnsRecordLocally(domain.getId(), "TXT", recordName, recordValue, recordId))
                        .then();
            } else if (provider.getType() == DnsProviderType.AWS_ROUTE53) {
                String accessKey = creds.get("accessKeyId").asText();
                String secretKey = creds.get("secretAccessKey").asText();
                String region = creds.has("region") ? creds.get("region").asText() : "us-east-1";
                
                return Mono.fromFuture(() -> route53ApiClient.getHostedZoneId(domain.getDomain(), accessKey, secretKey, region))
                        .flatMap(zoneId -> {
                            ResourceRecord record = ResourceRecord.builder().value("\"" + recordValue + "\"").build();
                            ResourceRecordSet recordSet = ResourceRecordSet.builder()
                                    .name(recordName + "." + domain.getDomain() + ".")
                                    .type(RRType.TXT)
                                    .ttl(3600L)
                                    .resourceRecords(record)
                                    .build();
                                    
                            Change change = Change.builder()
                                    .action(ChangeAction.UPSERT)
                                    .resourceRecordSet(recordSet)
                                    .build();
                                    
                            return Mono.fromFuture(() -> route53ApiClient.changeResourceRecordSets(zoneId, Collections.singletonList(change), accessKey, secretKey, region))
                                    .flatMap(changeInfo -> saveDnsRecordLocally(domain.getId(), "TXT", recordName, recordValue, changeInfo.id()))
                                    .then();
                        });
            }
        } catch (Exception e) {
            log.error("Failed to publish DNS record", e);
            return Mono.error(e);
        }
        return Mono.empty();
    }
    
    private Mono<DomainDnsRecord> saveDnsRecordLocally(Long domainId, String type, String name, String value, String providerRecordId) {
        return Mono.fromCallable(() -> {
            DomainDnsRecord record = DomainDnsRecord.builder()
                    .domainId(domainId)
                    .recordType(type)
                    .name(name)
                    .value(value)
                    .ttl(3600)
                    .providerRecordId(providerRecordId)
                    .managed(true)
                    .build();
            return dnsRecordRepository.save(record);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> configureMtaSigning(DkimKey dkimKey) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(dkimKey.getDomainId())
                    .orElseThrow(() -> new RuntimeException("Domain not found"));
            return domain;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(domain -> {
            String privateKeyBase64 = encryptionService.decrypt(dkimKey.getPrivateKey());
            String algorithmTag = dkimKey.getAlgorithm() == DkimAlgorithm.RSA_2048 ? "rsa" : "ed25519";

            Map<String, Object> body = Map.of(
                    "domain", domain.getDomain(),
                    "selector", dkimKey.getSelector(),
                    "privateKey", privateKeyBase64,
                    "algorithm", algorithmTag
            );

            return webClientBuilder.build()
                    .post()
                    .uri(robinServiceUrl + "/config/dkim")
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .doOnSuccess(r -> log.info("Configured MTA signing for domain {} with selector {}", domain.getDomain(), dkimKey.getSelector()))
                    .doOnError(e -> log.error("Failed to configure MTA signing for domain {}: {}", domain.getDomain(), e.getMessage()))
                    .then();
        });
    }
}
