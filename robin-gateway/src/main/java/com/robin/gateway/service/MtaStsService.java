package com.robin.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.integration.cloudflare.CloudflareApiClient;
import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.DnsProviderType;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.MtaStsPolicyMode;
import com.robin.gateway.model.MtaStsWorker;
import com.robin.gateway.model.MtaStsWorkerStatus;
import com.robin.gateway.model.DomainDnsRecord;
import com.robin.gateway.repository.DnsProviderRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.repository.MtaStsWorkerRepository;
import com.robin.gateway.repository.DomainDnsRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MtaStsService {

    private final MtaStsWorkerRepository mtaStsWorkerRepository;
    private final DomainRepository domainRepository;
    private final DnsProviderRepository dnsProviderRepository;
    private final DomainDnsRecordRepository dnsRecordRepository;
    private final EncryptionService encryptionService;
    private final CloudflareApiClient cloudflareApiClient;
    private final DnsResolverService dnsResolverService;
    private final ObjectMapper objectMapper;

    public Mono<Optional<MtaStsWorker>> getWorkerForDomain(Long domainId) {
        return Mono.fromCallable(() -> mtaStsWorkerRepository.findByDomainId(domainId))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(worker -> log.debug("Retrieved MTA-STS worker for domain {}", domainId))
                .doOnError(e -> log.error("Error retrieving MTA-STS worker for domain: {}", domainId, e));
    }

    @Transactional
    public Mono<MtaStsWorker> initiateWorkerDeployment(Long domainId) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            if (domain.getDnsProviderId() == null) {
                throw new RuntimeException("No DNS provider configured for domain: " + domainId);
            }

            DnsProvider provider = dnsProviderRepository.findById(domain.getDnsProviderId())
                    .orElseThrow(() -> new RuntimeException("DNS provider not found: " + domain.getDnsProviderId()));

            if (provider.getType() != DnsProviderType.CLOUDFLARE) {
                throw new RuntimeException("Automatic MTA-STS deployment is currently only supported for Cloudflare.");
            }
            return domain;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(domain -> {
            String workerName = "mta-sts-" + domain.getDomain().replace(".", "-");

            return Mono.fromCallable(() -> mtaStsWorkerRepository.findByDomainId(domainId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(existingOpt -> {
                        MtaStsWorker worker = existingOpt.orElseGet(() -> MtaStsWorker.builder()
                                .domainId(domainId)
                                .workerName(workerName)
                                .status(MtaStsWorkerStatus.PENDING)
                                .policyMode(MtaStsPolicyMode.testing)
                                .build());
                        
                        worker.setStatus(MtaStsWorkerStatus.PENDING);
                        return Mono.fromCallable(() -> mtaStsWorkerRepository.save(worker))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(saved -> doDeployCloudflareWorker(domain, saved));
                    });
        })
        .doOnError(e -> log.error("Error initiating MTA-STS worker deployment for domain: {}", domainId, e));
    }

    private Mono<MtaStsWorker> doDeployCloudflareWorker(Domain domain, MtaStsWorker worker) {
        return Mono.fromCallable(() -> {
            DnsProvider provider = dnsProviderRepository.findById(domain.getDnsProviderId())
                    .orElseThrow(() -> new RuntimeException("DNS provider not found"));
            String decryptedCreds = encryptionService.decrypt(provider.getCredentials());
            JsonNode creds = objectMapper.readTree(decryptedCreds);
            String apiToken = creds.get("apiToken").asText();
            String accountId = creds.has("accountId") ? creds.get("accountId").asText() : extractAccountIdFromToken(apiToken); // Simplified logic
            
            if (accountId == null) {
                throw new RuntimeException("Cloudflare accountId is required for MTA-STS deployment. Please re-configure provider credentials.");
            }
            
            return new ProviderContext(provider, apiToken, accountId);
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(ctx -> {
            String policy = generatePolicy(domain.getDomain(), worker.getPolicyMode());
            String kvNamespaceTitle = "mta-sts-" + domain.getDomain();

            return cloudflareApiClient.createKvNamespace(ctx.accountId, kvNamespaceTitle, ctx.apiToken)
                    .flatMap(namespaceId -> cloudflareApiClient.updateWorkerKv(ctx.accountId, namespaceId, "policy", policy, ctx.apiToken)
                        .then(getWorkerScriptTemplate())
                        .flatMap(script -> cloudflareApiClient.createWorkerScript(ctx.accountId, worker.getWorkerName(), script, ctx.apiToken))
                        .flatMap(scriptId -> {
                            worker.setWorkerId(scriptId);
                            worker.setPolicyVersion(String.valueOf(System.currentTimeMillis() / 1000));
                            return cloudflareApiClient.getZoneId(domain.getDomain(), ctx.apiToken);
                        })
                        .flatMap(zoneId -> {
                            String mtaStsDomain = "mta-sts." + domain.getDomain();
                            String routePattern = mtaStsDomain + "/.well-known/mta-sts.txt";
                            return cloudflareApiClient.createWorkerRoute(zoneId, routePattern, worker.getWorkerName(), ctx.apiToken)
                                    .then(cloudflareApiClient.createDnsRecord(zoneId, "TXT", "_mta-sts." + domain.getDomain(), "v=STSv1; id=" + worker.getPolicyVersion(), 1, ctx.apiToken))
                                    .flatMap(txtRecordId -> saveDnsRecordLocally(domain.getId(), "TXT", "_mta-sts." + domain.getDomain(), "v=STSv1; id=" + worker.getPolicyVersion(), txtRecordId))
                                    // A dummy A record might be required to trigger the route in CF if no origin exists, usually users use A 192.0.2.1
                                    .then(cloudflareApiClient.createDnsRecord(zoneId, "A", mtaStsDomain, "192.0.2.1", 1, ctx.apiToken).onErrorResume(e -> Mono.empty())) 
                                    .then(Mono.fromCallable(() -> {
                                        worker.setStatus(MtaStsWorkerStatus.DEPLOYED);
                                        worker.setDeployedAt(LocalDateTime.now());
                                        return mtaStsWorkerRepository.save(worker);
                                    }).subscribeOn(Schedulers.boundedElastic()));
                        })
                    );
        });
    }

    private String extractAccountIdFromToken(String token) {
        // Implementation omitted for brevity. User must provide accountId in Provider credentials JSON in real scenario.
        return null;
    }
    
    private Mono<DomainDnsRecord> saveDnsRecordLocally(Long domainId, String type, String name, String value, String providerRecordId) {
        return Mono.fromCallable(() -> {
            DomainDnsRecord record = DomainDnsRecord.builder()
                    .domainId(domainId)
                    .recordType(type)
                    .name(name)
                    .value(value)
                    .ttl(1)
                    .providerRecordId(providerRecordId)
                    .managed(true)
                    .build();
            return dnsRecordRepository.save(record);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<String> getWorkerScriptTemplate() {
        return Mono.fromCallable(() -> {
            ClassPathResource resource = new ClassPathResource("templates/mta-sts-worker.js");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String generatePolicy(String domain, MtaStsPolicyMode mode) {
        List<String> mxRecords = dnsResolverService.resolveMxRecords(domain);
        StringBuilder policy = new StringBuilder();
        policy.append("version: STSv1\n");
        policy.append("mode: ").append(mode.name()).append("\n");
        policy.append("max_age: 86400\n");

        for (String mx : mxRecords) {
            String[] parts = mx.split(" ", 2);
            if (parts.length > 1) {
                String mxHostname = parts[1].trim();
                if (mxHostname.endsWith(".")) mxHostname = mxHostname.substring(0, mxHostname.length() - 1);
                policy.append("mx: ").append(mxHostname).append("\n");
            }
        }
        
        if (mxRecords.isEmpty()) {
            policy.append("mx: mx.").append(domain).append("\n");
        }

        return policy.toString();
    }

    @Transactional
    public Mono<MtaStsWorker> updatePolicyMode(Long domainId, MtaStsPolicyMode mode) {
        return Mono.fromCallable(() -> {
            MtaStsWorker worker = mtaStsWorkerRepository.findByDomainId(domainId)
                    .orElseThrow(() -> new RuntimeException("MTA-STS worker not found for domain: " + domainId));

            worker.setPolicyMode(mode);
            MtaStsWorker saved = mtaStsWorkerRepository.save(worker);
            log.info("Updated MTA-STS policy mode to '{}' for domain {}", mode, domainId);
            return saved;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error updating MTA-STS policy mode for domain: {}", domainId, e));
    }
    
    private static class ProviderContext {
        final DnsProvider provider;
        final String apiToken;
        final String accountId;
        
        ProviderContext(DnsProvider provider, String apiToken, String accountId) {
            this.provider = provider;
            this.apiToken = apiToken;
            this.accountId = accountId;
        }
    }
}
