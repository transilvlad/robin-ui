package com.robin.gateway.service.dns;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.service.EncryptionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudflareDnsProvider implements DnsProvider {

    private final WebClient.Builder webClientBuilder;
    private final EncryptionService encryptionService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Data
    private static class CloudflareConfig {
        private String apiToken;
        private String zoneId;
    }

    private CloudflareConfig getConfig(Domain domain) {
        if (domain.getDnsProvider() == null) {
            throw new RuntimeException("Cloudflare provider not configured for this domain");
        }
        try {
            String decrypted = encryptionService.decrypt(domain.getDnsProvider().getCredentials());
            return objectMapper.readValue(decrypted, CloudflareConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse Cloudflare config", e);
            throw new RuntimeException("Invalid Cloudflare configuration");
        }
    }

    @Override
    public List<DnsRecord> listRecords(Domain domain) {
        CloudflareConfig config = getConfig(domain);
        log.info("Listing Cloudflare DNS records for domain: {}", domain.getDomain());
        
        return webClientBuilder.build()
                .get()
                .uri("https://api.cloudflare.com/client/v4/zones/" + config.getZoneId() + "/dns_records")
                .header("Authorization", "Bearer " + config.getApiToken())
                .retrieve()
                .bodyToMono(CloudflareResponse.class)
                .map(response -> {
                    // Map Cloudflare response to DnsRecord list
                    return List.<DnsRecord>of(); // Simplified for now
                })
                .block();
    }

    @Override
    public void createRecord(Domain domain, DnsRecord record) {
        CloudflareConfig config = getConfig(domain);
        log.info("Creating Cloudflare DNS record: {} {}", record.getType(), record.getName());
        
        webClientBuilder.build()
                .post()
                .uri("https://api.cloudflare.com/client/v4/zones/" + config.getZoneId() + "/dns_records")
                .header("Authorization", "Bearer " + config.getApiToken())
                .bodyValue(Map.of(
                        "type", record.getType().name(),
                        "name", record.getName(),
                        "content", record.getContent(),
                        "ttl", record.getTtl(),
                        "priority", record.getPriority() != null ? record.getPriority() : 10
                ))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void updateRecord(Domain domain, DnsRecord record) {
        CloudflareConfig config = getConfig(domain);
        log.info("Updating Cloudflare DNS record: {}", record.getExternalId());
        
        webClientBuilder.build()
                .put()
                .uri("https://api.cloudflare.com/client/v4/zones/" + config.getZoneId() + "/dns_records/" + record.getExternalId())
                .header("Authorization", "Bearer " + config.getApiToken())
                .bodyValue(Map.of(
                        "type", record.getType().name(),
                        "name", record.getName(),
                        "content", record.getContent(),
                        "ttl", record.getTtl()
                ))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void deleteRecord(Domain domain, String externalId) {
        CloudflareConfig config = getConfig(domain);
        log.info("Deleting Cloudflare DNS record: {}", externalId);
        
        webClientBuilder.build()
                .delete()
                .uri("https://api.cloudflare.com/client/v4/zones/" + config.getZoneId() + "/dns_records/" + externalId)
                .header("Authorization", "Bearer " + config.getApiToken())
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void enableDnssec(Domain domain) {
        CloudflareConfig config = getConfig(domain);
        log.info("Enabling DNSSEC on Cloudflare for: {}", domain.getDomain());
        
        webClientBuilder.build()
                .patch()
                .uri("https://api.cloudflare.com/client/v4/zones/" + config.getZoneId() + "/dnssec")
                .header("Authorization", "Bearer " + config.getApiToken())
                .bodyValue(Map.of("status", "active"))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void disableDnssec(Domain domain) {
        CloudflareConfig config = getConfig(domain);
        log.info("Disabling DNSSEC on Cloudflare for: {}", domain.getDomain());
        
        webClientBuilder.build()
                .patch()
                .uri("https://api.cloudflare.com/client/v4/zones/" + config.getZoneId() + "/dnssec")
                .header("Authorization", "Bearer " + config.getApiToken())
                .bodyValue(Map.of("status", "disabled"))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public List<DnsRecord> getDsRecords(Domain domain) {
        return List.of();
    }
}
