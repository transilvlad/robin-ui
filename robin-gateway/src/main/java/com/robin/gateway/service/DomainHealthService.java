package com.robin.gateway.service;

import com.robin.gateway.model.Domain;
import com.robin.gateway.model.DomainCheckType;
import com.robin.gateway.model.DomainHealth;
import com.robin.gateway.model.DomainHealthStatus;
import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.DkimKeyStatus;
import com.robin.gateway.repository.DkimKeyRepository;
import com.robin.gateway.repository.DomainHealthRepository;
import com.robin.gateway.repository.DomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainHealthService {

    private final DomainRepository domainRepository;
    private final DomainHealthRepository domainHealthRepository;
    private final DkimKeyRepository dkimKeyRepository;
    private final DnsResolverService dnsResolverService;

    @Transactional
    public Mono<List<DomainHealth>> runHealthChecksForDomain(Long domainId) {
        return Mono.fromCallable(() -> {
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId));

            List<DomainHealth> results = new ArrayList<>();

            for (DomainCheckType checkType : DomainCheckType.values()) {
                DomainHealth health = performCheck(domain, checkType);
                DomainHealth saved = domainHealthRepository.findByDomainIdAndCheckType(domainId, checkType)
                        .map(existing -> {
                            existing.setStatus(health.getStatus());
                            existing.setMessage(health.getMessage());
                            existing.setLastChecked(LocalDateTime.now());
                            return domainHealthRepository.save(existing);
                        })
                        .orElseGet(() -> domainHealthRepository.save(health));
                results.add(saved);
            }

            boolean anyError = results.stream().anyMatch(r -> r.getStatus() == DomainHealthStatus.ERROR);
            domain.setStatus(anyError ? "ERROR" : "ACTIVE");
            domain.setLastHealthCheck(LocalDateTime.now());
            domainRepository.save(domain);

            log.info("Completed health checks for domain {}", domain.getDomain());
            return results;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(e -> log.error("Error running health checks for domain: {}", domainId, e));
    }

    public Mono<List<DomainHealth>> getHealthForDomain(Long domainId) {
        return Mono.fromCallable(() -> domainHealthRepository.findByDomainId(domainId))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(health -> log.debug("Retrieved {} health records for domain {}", health.size(), domainId))
                .doOnError(e -> log.error("Error retrieving health records for domain: {}", domainId, e));
    }

    @Scheduled(fixedRate = 3600000) // 1 hour
    public void runScheduledHealthChecks() {
        log.info("Running scheduled health checks for all domains");
        domainRepository.findAll().forEach(domain -> {
            try {
                runHealthChecksForDomain(domain.getId()).block();
            } catch (Exception e) {
                log.error("Error running scheduled health check for domain {}: {}", domain.getDomain(), e.getMessage());
            }
        });
    }

    private DomainHealth performCheck(Domain domain, DomainCheckType checkType) {
        String domainName = domain.getDomain();
        DomainHealthStatus status = DomainHealthStatus.UNKNOWN;
        String message = "Check failed to execute";

        try {
            switch (checkType) {
                case MX -> {
                    List<String> mxRecords = dnsResolverService.resolveMxRecords(domainName);
                    if (mxRecords.isEmpty()) {
                        status = DomainHealthStatus.ERROR;
                        message = "No MX records found for " + domainName;
                    } else {
                        status = DomainHealthStatus.OK;
                        message = "Found " + mxRecords.size() + " MX records";
                    }
                }
                case SPF -> {
                    List<String> txtRecords = dnsResolverService.resolveTxtRecords(domainName);
                    Optional<String> spfRecord = txtRecords.stream().filter(r -> r.startsWith("v=spf1")).findFirst();
                    if (spfRecord.isEmpty()) {
                        status = DomainHealthStatus.ERROR;
                        message = "No SPF record found";
                    } else {
                        status = DomainHealthStatus.OK;
                        message = "SPF record is valid: " + spfRecord.get();
                    }
                }
                case DKIM -> {
                    List<DkimKey> activeKeys = dkimKeyRepository.findByDomainIdAndStatus(domain.getId(), DkimKeyStatus.ACTIVE);
                    if (activeKeys.isEmpty()) {
                        status = DomainHealthStatus.WARN;
                        message = "No active DKIM key configured in the system";
                    } else {
                        boolean allValid = true;
                        StringBuilder msg = new StringBuilder();
                        for (DkimKey key : activeKeys) {
                            List<String> txtRecords = dnsResolverService.resolveTxtRecords(key.getSelector() + "._domainkey." + domainName);
                            Optional<String> dkimRecord = txtRecords.stream().filter(r -> r.startsWith("v=DKIM1")).findFirst();
                            if (dkimRecord.isEmpty()) {
                                allValid = false;
                                msg.append("Selector ").append(key.getSelector()).append(" is missing or invalid. ");
                            }
                        }
                        if (allValid) {
                            status = DomainHealthStatus.OK;
                            message = "All active DKIM keys have valid DNS records";
                        } else {
                            status = DomainHealthStatus.ERROR;
                            message = msg.toString();
                        }
                    }
                }
                case DMARC -> {
                    List<String> txtRecords = dnsResolverService.resolveTxtRecords("_dmarc." + domainName);
                    Optional<String> dmarcRecord = txtRecords.stream().filter(r -> r.startsWith("v=DMARC1")).findFirst();
                    if (dmarcRecord.isEmpty()) {
                        status = DomainHealthStatus.ERROR;
                        message = "No DMARC record found at _dmarc." + domainName;
                    } else {
                        status = DomainHealthStatus.OK;
                        message = "DMARC record is valid: " + dmarcRecord.get();
                    }
                }
                case MTA_STS -> {
                    List<String> txtRecords = dnsResolverService.resolveTxtRecords("_mta-sts." + domainName);
                    Optional<String> mtaStsRecord = txtRecords.stream().filter(r -> r.startsWith("v=STSv1")).findFirst();
                    if (mtaStsRecord.isEmpty()) {
                        status = DomainHealthStatus.ERROR;
                        message = "No MTA-STS TXT record found at _mta-sts." + domainName;
                    } else {
                        status = DomainHealthStatus.OK;
                        message = "MTA-STS TXT record is valid: " + mtaStsRecord.get();
                    }
                }
                case NS -> {
                    List<String> nsRecords = dnsResolverService.resolveNsRecords(domainName);
                    if (nsRecords.isEmpty()) {
                        status = DomainHealthStatus.ERROR;
                        message = "No NS records found for " + domainName;
                    } else {
                        status = DomainHealthStatus.OK;
                        message = "Found " + nsRecords.size() + " NS records";
                    }
                }
            }
        } catch (Exception e) {
            log.error("Exception during health check {} for domain {}", checkType, domainName, e);
            status = DomainHealthStatus.ERROR;
            message = "Error executing check: " + e.getMessage();
        }

        return DomainHealth.builder()
                .domainId(domain.getId())
                .checkType(checkType)
                .status(status)
                .message(message)
                .build();
    }
}
