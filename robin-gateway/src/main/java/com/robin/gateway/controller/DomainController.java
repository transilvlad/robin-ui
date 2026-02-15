package com.robin.gateway.controller;

import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DnsRecordRepository;
import com.robin.gateway.service.DomainService;
import com.robin.gateway.service.DomainSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/domains")
@RequiredArgsConstructor
@Tag(name = "Domain Management", description = "APIs for managing mail domains and DNS configuration")
public class DomainController {

    private final DomainService domainService;
    private final DomainSyncService domainSyncService;
    private final DnsRecordRepository dnsRecordRepository;
    private final com.robin.gateway.service.DnsDiscoveryService dnsDiscoveryService;

    @GetMapping
    @Operation(summary = "List all domains", description = "Returns a paginated list of managed domains")
    public Mono<Page<Domain>> getAllDomains(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return domainService.getAllDomains(pageable)
                .doOnError(e -> System.err.println("Error in getAllDomains controller: " + e.getMessage()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get domain by ID", description = "Returns detailed information about a specific domain")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved domain"),
        @ApiResponse(responseCode = "404", description = "Domain not found")
    })
    public Mono<Domain> getDomain(@Parameter(description = "Internal domain ID") @PathVariable Long id) {
        return domainService.getDomainById(id);
    }

    @PostMapping("/discover")
    @Operation(summary = "Discover domain settings", description = "Performs DNS lookup and API discovery to detect existing domain configuration")
    public Mono<com.robin.gateway.service.DnsDiscoveryService.DiscoveryResult> discoverDomain(@Valid @RequestBody DiscoverDomainRequest request) {
        return dnsDiscoveryService.discover(request.getDomain(), request.getDnsProviderId());
    }

    @PostMapping
    @Operation(summary = "Create a new domain", description = "Registers a new domain and optionally sets initial DNS records")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Domain created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public Mono<Domain> createDomain(@Valid @RequestBody CreateDomainRequest request) {
        return domainService.createDomain(
            request.getDomain(), 
            request.getDnsProviderId(), 
            request.getRegistrarProviderId(),
            request.getEmailProviderId(),
            request.getConfig(),
            request.getInitialRecords()
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update domain", description = "Updates domain configuration settings (DMARC, SPF, etc.)")
    public Mono<Domain> updateDomain(@PathVariable Long id, @Valid @RequestBody Domain domain) {
        return domainService.updateDomain(id, domain);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete domain", description = "Removes a domain from management")
    public Mono<Void> deleteDomain(@PathVariable Long id) {
        return domainService.deleteDomain(id);
    }

    @Schema(description = "Request for domain discovery")
    @Data
    public static class DiscoverDomainRequest {
        @Schema(description = "Domain name to discover", example = "example.com")
        @NotBlank(message = "Domain is required")
        private String domain;
        
        @Schema(description = "ID of the DNS provider to use for API discovery")
        @NotNull(message = "DNS Provider ID is required")
        private Long dnsProviderId;
    }

    @Schema(description = "Request for creating a new domain")
    @Data
    public static class CreateDomainRequest {
        @Schema(description = "Domain name", example = "newdomain.com")
        @NotBlank(message = "Domain is required")
        private String domain;
        
        @Schema(description = "ID of the DNS provider for this domain")
        @NotNull(message = "DNS Provider ID is required")
        private Long dnsProviderId;
        
        @Schema(description = "Optional ID of the registrar provider")
        private Long registrarProviderId;

        @Schema(description = "Optional ID of the email provider")
        private Long emailProviderId;

        @Schema(description = "Optional initial domain configuration")
        private Domain config;

        @Schema(description = "Optional list of initial DNS records to create")
        private List<InitialRecordRequest> initialRecords;
    }

    @Schema(description = "Request for creating an initial DNS record")
    @Data
    public static class InitialRecordRequest {
        @Schema(description = "DNS record type")
        @NotNull(message = "Type is required")
        private com.robin.gateway.model.DnsRecord.RecordType type;
        
        @Schema(description = "Record name (e.g. '@', 'mail', 'www')", example = "mail")
        @NotBlank(message = "Name is required")
        private String name;
        
        @Schema(description = "Record content/value", example = "1.2.3.4")
        @NotBlank(message = "Content is required")
        private String content;
        
        @Schema(description = "TTL in seconds", example = "3600")
        private Integer ttl;

        @Schema(description = "MX record priority", example = "10")
        private Integer priority;

        @Schema(description = "Robin-specific purpose for this record")
        private com.robin.gateway.model.DnsRecord.RecordPurpose purpose;
    }

    @GetMapping("/{id}/records")
    @Operation(summary = "Get domain DNS records", description = "Returns all DNS records associated with this domain")
    public Mono<List<com.robin.gateway.model.DnsRecord>> getRecords(@PathVariable Long id) {
        return Mono.fromCallable(() -> dnsRecordRepository.findByDomain_Id(id))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "Sync domain with provider", description = "Pushes local DNS changes to the remote DNS provider")
    public Mono<Void> syncDomain(@PathVariable Long id) {
        return domainSyncService.syncDomain(id);
    }

    @GetMapping("/{id}/dnssec")
    @Operation(summary = "Get DNSSEC status", description = "Returns current DNSSEC configuration and DS records")
    public Mono<List<com.robin.gateway.model.DnsRecord>> getDnssecStatus(@PathVariable Long id) {
        return domainService.getDnssecStatus(id);
    }

    @PostMapping("/{id}/dnssec/enable")
    @Operation(summary = "Enable DNSSEC", description = "Triggers DNSSEC enablement on the provider")
    public Mono<Void> enableDnssec(@PathVariable Long id) {
        return domainService.enableDnssec(id);
    }

    @PostMapping("/{id}/dnssec/disable")
    @Operation(summary = "Disable DNSSEC", description = "Triggers DNSSEC disablement on the provider")
    public Mono<Void> disableDnssec(@PathVariable Long id) {
        return domainService.disableDnssec(id);
    }
}
