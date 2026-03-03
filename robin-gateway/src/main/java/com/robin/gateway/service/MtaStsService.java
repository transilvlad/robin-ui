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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MtaStsService {
    private static final Pattern POLICY_ID_PATTERN = Pattern.compile("\\bid\\s*=\\s*([^;\\s\"]+)");

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

    public Mono<Void> ensureWorkerForDomain(Long domainId, MtaStsPolicyMode policyMode) {
        return reconcileExistingInfrastructure(domainId)
                .flatMap(reconciled -> {
                    if (reconciled) {
                        log.info("Reconciled existing MTA-STS infrastructure for domain {}", domainId);
                        return Mono.empty();
                    }
                    return initiateWorkerDeployment(domainId, policyMode).then();
                });
    }

    @Transactional
    public Mono<Boolean> reconcileExistingInfrastructure(Long domainId) {
        return Mono.fromCallable(() -> domainRepository.findById(domainId)
                        .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(domain -> resolveProviderContext(domain)
                        .map(providerCtx -> new ReconciliationContext(
                                domain,
                                providerCtx.apiToken,
                                providerCtx.accountId,
                                "mta-sts-" + domain.getDomain().replace(".", "-"),
                                "_mta-sts." + domain.getDomain())))
                .flatMap(ctx -> cloudflareApiClient.getZoneId(ctx.domain.getDomain(), ctx.apiToken)
                        .flatMap(zoneId -> {
                            Mono<Optional<String>> workerIdMono = cloudflareApiClient
                                    .getWorkerScriptId(ctx.accountId, ctx.workerName, ctx.apiToken)
                                    .map(Optional::of)
                                    .defaultIfEmpty(Optional.empty());

                            Mono<Optional<CloudflareTxtRecord>> txtMono = cloudflareApiClient
                                    .listDnsRecords(zoneId, ctx.apiToken)
                                    .map(records -> Optional.ofNullable(findMtaStsTxtRecord(records, ctx.txtName)));

                            return Mono.zip(workerIdMono, txtMono)
                                    .flatMap(tuple -> Mono.fromCallable(() -> {
                                        Optional<String> workerIdOpt = tuple.getT1();
                                        Optional<CloudflareTxtRecord> txtRecordOpt = tuple.getT2();
                                        if (workerIdOpt.isEmpty() && txtRecordOpt.isEmpty()) {
                                            return false;
                                        }

                                        MtaStsWorker worker = mtaStsWorkerRepository.findByDomainId(domainId)
                                                .orElseGet(() -> MtaStsWorker.builder()
                                                        .domainId(domainId)
                                                        .workerName(ctx.workerName)
                                                        .build());

                                        worker.setWorkerName(ctx.workerName);
                                        worker.setStatus(MtaStsWorkerStatus.DEPLOYED);
                                        if (worker.getPolicyMode() == null) {
                                            worker.setPolicyMode(MtaStsPolicyMode.testing);
                                        }
                                        if (worker.getDeployedAt() == null) {
                                            worker.setDeployedAt(LocalDateTime.now());
                                        }

                                        workerIdOpt.ifPresent(worker::setWorkerId);

                                        txtRecordOpt.ifPresent(txt -> {
                                            String normalizedValue = normalizeTxtValue(txt.content());
                                            extractPolicyVersion(normalizedValue).ifPresent(worker::setPolicyVersion);
                                            upsertLocalMtaStsTxtRecord(domainId, ctx.txtName, normalizedValue, txt.id());
                                        });

                                        mtaStsWorkerRepository.save(worker);
                                        return true;
                                    }).subscribeOn(Schedulers.boundedElastic()));
                        }))
                .onErrorResume(e -> {
                    log.warn("MTA-STS reconciliation skipped for domain {}: {}", domainId, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Transactional
    public Mono<MtaStsWorker> initiateWorkerDeployment(Long domainId, MtaStsPolicyMode policyMode) {
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
                                .build());

                        worker.setStatus(MtaStsWorkerStatus.PENDING);
                        worker.setPolicyMode(policyMode);
                        return Mono.fromCallable(() -> mtaStsWorkerRepository.save(worker))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(saved -> doDeployCloudflareWorker(domain, saved));
                    });
        })
        .doOnError(e -> log.error("Error initiating MTA-STS worker deployment for domain: {}", domainId, e));
    }

    private Mono<MtaStsWorker> doDeployCloudflareWorker(Domain domain, MtaStsWorker worker) {
        return resolveProviderContext(domain).flatMap(ctx -> {
            String policy = generatePolicy(domain.getDomain(), worker.getPolicyMode());
            worker.setPolicyContent(policy);
            String kvNamespaceTitle = "mta-sts-" + domain.getDomain();

            return cloudflareApiClient.createKvNamespace(ctx.accountId, kvNamespaceTitle, ctx.apiToken)
                    .flatMap(namespaceId -> cloudflareApiClient.updateWorkerKv(ctx.accountId, namespaceId, "policy", policy, ctx.apiToken)
                        .then(getWorkerScriptTemplate())
                        .flatMap(script -> cloudflareApiClient.createWorkerScript(ctx.accountId, worker.getWorkerName(), script, namespaceId, ctx.apiToken))
                        .flatMap(scriptId -> {
                            worker.setWorkerId(scriptId);
                            worker.setPolicyVersion(String.valueOf(System.currentTimeMillis() / 1000));
                            return cloudflareApiClient.getZoneId(domain.getDomain(), ctx.apiToken);
                        })
                        .flatMap(zoneId -> {
                            String mtaStsDomain = "mta-sts." + domain.getDomain();
                            String txtName = "_mta-sts." + domain.getDomain();
                            String txtValue = "\"v=STSv1; id=" + worker.getPolicyVersion() + "\"";
                            return cloudflareApiClient.addWorkerCustomDomain(ctx.accountId, mtaStsDomain, zoneId, worker.getWorkerName(), ctx.apiToken)
                                    .then(cloudflareApiClient.createDnsRecord(zoneId, "TXT", txtName, txtValue, 1, ctx.apiToken))
                                    .flatMap(txtRecordId -> saveDnsRecordLocally(domain.getId(), "TXT", txtName, txtValue, txtRecordId))
                                    .then(Mono.fromCallable(() -> {
                                        worker.setStatus(MtaStsWorkerStatus.DEPLOYED);
                                        worker.setDeployedAt(LocalDateTime.now());
                                        return mtaStsWorkerRepository.save(worker);
                                    }).subscribeOn(Schedulers.boundedElastic()));
                        })
                    );
        });
    }

    private Mono<ProviderContext> resolveProviderContext(Domain domain) {
        return Mono.fromCallable(() -> {
            if (domain.getDnsProviderId() == null) {
                throw new RuntimeException("No DNS provider configured for domain: " + domain.getId());
            }

            DnsProvider provider = dnsProviderRepository.findById(domain.getDnsProviderId())
                    .orElseThrow(() -> new RuntimeException("DNS provider not found"));

            if (provider.getType() != DnsProviderType.CLOUDFLARE) {
                throw new RuntimeException("Automatic MTA-STS deployment is currently only supported for Cloudflare.");
            }

            String decryptedCreds = encryptionService.decrypt(provider.getCredentials());
            JsonNode creds = objectMapper.readTree(decryptedCreds);
            if (creds.get("apiToken") == null || creds.get("apiToken").asText().isBlank()) {
                throw new RuntimeException("Cloudflare apiToken is missing in provider credentials");
            }
            String apiToken = creds.get("apiToken").asText();
            String accountId = (creds.has("accountId") && !creds.get("accountId").asText().isBlank())
                    ? creds.get("accountId").asText() : null;
            return new ProviderContext(provider, apiToken, accountId);
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(ctx -> {
                    if (ctx.accountId != null) {
                        return Mono.just(ctx);
                    }
                    log.info("accountId not found in credentials for domain {}, fetching from Cloudflare API", domain.getDomain());
                    return cloudflareApiClient.getAccountId(ctx.apiToken)
                            .map(accountId -> new ProviderContext(ctx.provider, ctx.apiToken, accountId));
                });
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

        boolean mxAdded = false;
        for (String mx : mxRecords) {
            String[] parts = mx.split(" ", 2);
            if (parts.length > 1) {
                String mxHostname = parts[1].trim();
                if (mxHostname.endsWith(".")) mxHostname = mxHostname.substring(0, mxHostname.length() - 1);
                if (isPublicHostname(mxHostname)) {
                    policy.append("mx: ").append(mxHostname).append("\n");
                    mxAdded = true;
                } else {
                    log.warn("Skipping local/private MX hostname '{}' from MTA-STS policy for domain {}", mxHostname, domain);
                }
            }
        }

        if (!mxAdded) {
            policy.append("mx: mx.").append(domain).append("\n");
        }

        return policy.toString();
    }

    private CloudflareTxtRecord findMtaStsTxtRecord(JsonNode records, String txtName) {
        if (records == null || !records.isArray()) {
            return null;
        }
        for (JsonNode node : records) {
            if (!"TXT".equalsIgnoreCase(node.path("type").asText())) {
                continue;
            }
            if (!txtName.equalsIgnoreCase(node.path("name").asText())) {
                continue;
            }
            String recordId = node.path("id").asText(null);
            String content = node.path("content").asText("");
            return new CloudflareTxtRecord(recordId, content);
        }
        return null;
    }

    private void upsertLocalMtaStsTxtRecord(Long domainId, String txtName, String txtValue, String providerRecordId) {
        DomainDnsRecord existing = dnsRecordRepository.findByDomainIdAndRecordType(domainId, "TXT").stream()
                .filter(record -> txtName.equalsIgnoreCase(record.getName()))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            existing = DomainDnsRecord.builder()
                    .domainId(domainId)
                    .recordType("TXT")
                    .name(txtName)
                    .ttl(1)
                    .managed(true)
                    .build();
        }

        existing.setValue(txtValue);
        existing.setProviderRecordId(providerRecordId);
        existing.setManaged(true);
        if (existing.getTtl() == null) {
            existing.setTtl(1);
        }
        dnsRecordRepository.save(existing);
    }

    private Optional<String> extractPolicyVersion(String txtValue) {
        Matcher matcher = POLICY_ID_PATTERN.matcher(txtValue);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private String normalizeTxtValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Returns true if the hostname is a publicly routable domain, i.e. not a
     * local, private, or reserved TLD (RFC 2606 / RFC 8375 + common conventions).
     */
    private boolean isPublicHostname(String hostname) {
        if (hostname == null || hostname.isBlank()) return false;
        String lower = hostname.toLowerCase();
        for (String suffix : List.of(".local", ".localhost", ".internal", ".test",
                ".example", ".invalid", ".lan", ".home", ".corp", ".intranet")) {
            if (lower.endsWith(suffix)) return false;
        }
        return !lower.equals("localhost");
    }

    /**
     * Replaces the stored policy content both in the local database and in the
     * Cloudflare KV store if the worker has been deployed.
     */
    @Transactional
    public Mono<MtaStsWorker> updatePolicyContent(Long domainId, String content) {
        return Mono.fromCallable(() -> {
            MtaStsWorker worker = mtaStsWorkerRepository.findByDomainId(domainId)
                    .orElseThrow(() -> new RuntimeException("MTA-STS worker not found for domain: " + domainId));

            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            worker.setPolicyContent(content);
            MtaStsWorker saved = mtaStsWorkerRepository.save(worker);
            log.info("Updated MTA-STS policy content for domain {}", domainId);
            return new Object[]{ saved, domain };
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(arr -> {
            MtaStsWorker saved = (MtaStsWorker) arr[0];
            Domain domain = (Domain) arr[1];

            if (saved.getStatus() != MtaStsWorkerStatus.DEPLOYED || domain.getDnsProviderId() == null) {
                return Mono.just(saved);
            }

            return Mono.fromCallable(() -> {
                DnsProvider provider = dnsProviderRepository.findById(domain.getDnsProviderId())
                        .orElseThrow(() -> new RuntimeException("DNS provider not found"));
                String decrypted = encryptionService.decrypt(provider.getCredentials());
                JsonNode creds = objectMapper.readTree(decrypted);
                String apiToken = creds.get("apiToken").asText();
                String accountId = creds.has("accountId") ? creds.get("accountId").asText() : null;
                return new String[]{ apiToken, accountId };
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(creds -> {
                String apiToken = creds[0];
                String accountId = creds[1];
                if (accountId == null) return Mono.just(saved);
                String kvNamespaceTitle = "mta-sts-" + domain.getDomain();
                return cloudflareApiClient.createKvNamespace(accountId, kvNamespaceTitle, apiToken)
                        .flatMap(nsId -> cloudflareApiClient.updateWorkerKv(accountId, nsId, "policy", content, apiToken))
                        .thenReturn(saved)
                        .onErrorResume(e -> {
                            log.warn("Could not update Cloudflare KV for domain {}: {}", domainId, e.getMessage());
                            return Mono.just(saved);
                        });
            });
        })
        .doOnError(e -> log.error("Error updating MTA-STS policy content for domain: {}", domainId, e));
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

    private record ReconciliationContext(
            Domain domain,
            String apiToken,
            String accountId,
            String workerName,
            String txtName) {
    }

    private record CloudflareTxtRecord(String id, String content) {
    }
}
