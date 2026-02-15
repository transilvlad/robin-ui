package com.robin.gateway.controller;

import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DomainRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequiredArgsConstructor
@Tag(name = "MTA-STS", description = "Endpoints for MTA-STS policy delivery")
public class MtaStsController {

    private final DomainRepository domainRepository;

    @GetMapping(value = "/.well-known/mta-sts.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Get MTA-STS policy", description = "Returns the MTA-STS policy file for the requested domain (Host header)")
    public Mono<ResponseEntity<String>> getMtaStsPolicy(
            @Parameter(description = "Host header containing the domain or mta-sts subdomain") @RequestHeader("Host") String host) {
        return Mono.fromCallable(() -> {
            // host might be mta-sts.example.com
            String domainName = host;
            if (host.startsWith("mta-sts.")) {
                domainName = host.substring(8);
            }

            return domainRepository.findByDomain(domainName)
                    .filter(Domain::getMtaStsEnabled)
                    .map(domain -> {
                        String policy = "version: STSv1\n" +
                                "mode: " + domain.getMtaStsMode().name().toLowerCase() + "\n" +
                                "mx: mail." + domain.getDomain() + "\n" +
                                "max_age: 604800\n";
                        return ResponseEntity.ok(policy);
                    })
                    .orElse(ResponseEntity.notFound().build());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
