package com.robin.gateway.service;

import com.robin.gateway.model.Domain;
import com.robin.gateway.model.MtaStsPolicyMode;
import com.robin.gateway.model.MtaStsWorker;
import com.robin.gateway.model.MtaStsWorkerStatus;
import com.robin.gateway.repository.DnsProviderRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.repository.MtaStsWorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MtaStsService {

    private final MtaStsWorkerRepository mtaStsWorkerRepository;
    private final DomainRepository domainRepository;
    private final DnsProviderRepository dnsProviderRepository;

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

            String workerName = "mta-sts-" + domain.getDomain();

            MtaStsWorker worker = MtaStsWorker.builder()
                    .domainId(domainId)
                    .workerName(workerName)
                    .status(MtaStsWorkerStatus.PENDING)
                    .build();

            MtaStsWorker saved = mtaStsWorkerRepository.save(worker);
            log.info("Cloudflare Worker deployment pending implementation for domain '{}' (worker='{}')",
                    domain.getDomain(), workerName);
            return saved;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error initiating MTA-STS worker deployment for domain: {}", domainId, e));
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
}
