package com.robin.gateway.service;

import com.robin.gateway.model.DomainCheckType;
import com.robin.gateway.model.DomainHealth;
import com.robin.gateway.model.DomainHealthStatus;
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

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainHealthService {

    private final DomainRepository domainRepository;
    private final DomainHealthRepository domainHealthRepository;
    private final DkimKeyRepository dkimKeyRepository;

    @Transactional
    public Mono<List<DomainHealth>> runHealthChecksForDomain(Long domainId) {
        return Mono.fromCallable(() -> {
            if (!domainRepository.existsById(domainId)) {
                throw new RuntimeException("Domain not found: " + domainId);
            }

            List<DomainHealth> results = new ArrayList<>();

            for (DomainCheckType checkType : DomainCheckType.values()) {
                DomainHealth health = performStubCheck(domainId, checkType);
                DomainHealth saved = domainHealthRepository.findByDomainIdAndCheckType(domainId, checkType)
                        .map(existing -> {
                            existing.setStatus(health.getStatus());
                            existing.setMessage(health.getMessage());
                            return domainHealthRepository.save(existing);
                        })
                        .orElseGet(() -> domainHealthRepository.save(health));
                results.add(saved);
            }

            log.info("Completed health checks for domain {}", domainId);
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

    @Scheduled(fixedRate = 3600000)
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

    private DomainHealth performStubCheck(Long domainId, DomainCheckType checkType) {
        log.debug("DNS resolution for check type {} on domain {} is pending real implementation", checkType, domainId);

        String message = buildStubMessage(checkType);

        return DomainHealth.builder()
                .domainId(domainId)
                .checkType(checkType)
                .status(DomainHealthStatus.UNKNOWN)
                .message(message)
                .build();
    }

    private String buildStubMessage(DomainCheckType checkType) {
        return switch (checkType) {
            case MX -> "MX record check pending DNS resolution implementation";
            case SPF -> "SPF record check pending DNS resolution implementation";
            case DKIM -> "DKIM record check pending DNS resolution implementation";
            case DMARC -> "DMARC record check pending DNS resolution implementation";
            case MTA_STS -> "MTA-STS policy check pending DNS resolution implementation";
            case NS -> "NS delegation check pending DNS resolution implementation";
        };
    }
}
