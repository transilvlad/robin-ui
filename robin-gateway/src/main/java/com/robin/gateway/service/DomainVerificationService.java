package com.robin.gateway.service;

import com.robin.gateway.model.Domain;
import com.robin.gateway.model.DomainCheckType;
import com.robin.gateway.model.DomainHealth;
import com.robin.gateway.repository.DomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainVerificationService {

    private final DomainHealthService domainHealthService;
    private final DomainRepository domainRepository;

    public Mono<List<DomainHealth>> verifyDomain(Long domainId) {
        return Mono.fromCallable(() -> domainRepository.findById(domainId)
                .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId)))
                .flatMap(domain -> {
                    log.info("Running on-demand verification for domain: {}", domain.getDomain());
                    return domainHealthService.runHealthChecksForDomain(domainId);
                });
    }
}
