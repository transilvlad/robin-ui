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
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CloudflareResponse {
        private List<CloudflareResult> result;
        private boolean success;
        private List<CloudflareError> errors;
    }

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CloudflareResult {
        private String id;
        private String type;
        private String name;
        private String content;
        private Integer ttl;
        private Integer priority;
    }

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CloudflareError {
        private int code;
        private String message;
    }

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CloudflareConfig {
        private String apiToken;
        private String zoneId;
    }

    private CloudflareConfig getConfig(Domain domain) {
        if (domain.getDnsProvider() == null) {
            throw new RuntimeException("Cloudflare provider not configured for this domain");
        }
        try {
            String decrypted = encryptionService.decrypt(domain.getDnsProvider().getCredentials());
            CloudflareConfig config = objectMapper.readValue(decrypted, CloudflareConfig.class);
            if (config.getApiToken() == null || config.getZoneId() == null) {
                throw new RuntimeException("Missing apiToken or zoneId");
            }
            return config;
        } catch (Exception e) {
            log.error("Failed to parse Cloudflare config", e);
            throw new RuntimeException("Invalid Cloudflare configuration: " + e.getMessage());
        }
    }

    @Override
    public List<DnsRecord> listRecords(Domain domain) {
        CloudflareConfig config = getConfig(domain);
        log.info("Listing Cloudflare DNS records for domain: {}", domain.getDomain());
        
        try {
            CloudflareResponse response = webClientBuilder.build()
                    .get()
                    .uri("https://api.cloudflare.com/client/v4/zones/" + config.getZoneId() + "/dns_records?per_page=100")
                    .header("Authorization", "Bearer " + config.getApiToken())
                    .retrieve()
                    .bodyToMono(CloudflareResponse.class)
                    .block();

            if (response == null || !response.isSuccess()) {
                return List.of();
            }

            return response.getResult().stream().map(r -> {
                DnsRecord.RecordType type;
                try {
                    type = DnsRecord.RecordType.valueOf(r.getType());
                } catch (Exception e) {
                    type = DnsRecord.RecordType.TXT; // Fallback
                }

                String content = r.getContent();
                String name = r.getName();

                // Normalize root domain to @
                if (name.equals(domain.getDomain())) {
                    name = "@";
                }

                // Normalize TXT content (strip quotes)
                if (type == DnsRecord.RecordType.TXT && content.startsWith("\"") && content.endsWith("\"")) {
                    content = content.substring(1, content.length() - 1);
                }

                DnsRecord.RecordPurpose purpose = DnsRecord.RecordPurpose.OTHER;
                if (type == DnsRecord.RecordType.MX) purpose = DnsRecord.RecordPurpose.MX;
                else if (content.contains("v=spf1")) purpose = DnsRecord.RecordPurpose.SPF;
                else if (content.contains("v=DMARC1")) purpose = DnsRecord.RecordPurpose.DMARC;
                else if (name.contains("_domainkey")) purpose = DnsRecord.RecordPurpose.DKIM;
                else if (name.contains("_mta-sts") || name.startsWith("mta-sts.")) purpose = DnsRecord.RecordPurpose.MTA_STS_RECORD;

                return DnsRecord.builder()
                        .type(type)
                        .name(name)
                        .content(content)
                        .externalId(r.getId())
                        .priority(r.getPriority())
                        .ttl(r.getTtl())
                        .purpose(purpose)
                        .build();
            }).toList();
        } catch (Exception e) {
            log.error("Failed to list Cloudflare records", e);
            return List.of();
        }
    }

    @Override
    public void createRecord(Domain domain, DnsRecord record) {
        DnsRecord.RecordType type = record.getType();
        log.info("Processing Cloudflare sync for record: {} ({})", record.getName(), type);

        // Cloudflare standard zone API has limitations on certain record types or requires special payload formats (SRV).
        // Skipping these for now to ensure primary email records (A, MX, TXT, CNAME) are synced successfully.
        if (type == DnsRecord.RecordType.PTR || 
            type == DnsRecord.RecordType.TLSA || 
            type == DnsRecord.RecordType.DS || 
            type == DnsRecord.RecordType.SRV) {
            log.info("Skipping Cloudflare sync for record type: {}", type);
            return;
        }

        CloudflareConfig config = getConfig(domain);
        String typeName = type.name();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("type", typeName);
        body.put("name", record.getName());
        
        String content = record.getContent();
        if (type == DnsRecord.RecordType.TXT && content != null && !content.startsWith("\"")) {
            content = "\"" + content + "\"";
        }
        body.put("content", content);
        
        body.put("ttl", record.getTtl());
        body.put("proxied", false);

        if (type == DnsRecord.RecordType.MX) {
            body.put("priority", record.getPriority() != null ? record.getPriority() : 10);
        }

        try {
            webClientBuilder.build()
                    .post()
                    .uri("https://api.cloudflare.com/client/v4/zones/" + config.getZoneId() + "/dns_records")
                    .header("Authorization", "Bearer " + config.getApiToken())
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody.contains("81058") || responseBody.contains("already exists")) {
                log.info("Cloudflare record already exists, skipping: {} {}", record.getType(), record.getName());
                return;
            }
            log.error("Cloudflare API error ({}): {}", e.getStatusCode(), responseBody);
            throw new RuntimeException("Cloudflare API error: " + responseBody);
        }
    }

    @Override
    public void updateRecord(Domain domain, DnsRecord record) {
        DnsRecord.RecordType type = record.getType();
        if (type == DnsRecord.RecordType.PTR || 
            type == DnsRecord.RecordType.TLSA || 
            type == DnsRecord.RecordType.DS || 
            type == DnsRecord.RecordType.SRV) {
            return;
        }

        CloudflareConfig config = getConfig(domain);
        String typeName = type.name();
        log.info("Updating Cloudflare DNS record: {} (ID: {})", record.getName(), record.getExternalId());
        
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("type", typeName);
        body.put("name", record.getName());

        String content = record.getContent();
        if (type == DnsRecord.RecordType.TXT && content != null && !content.startsWith("\"")) {
            content = "\"" + content + "\"";
        }
        body.put("content", content);

        body.put("ttl", record.getTtl());
        body.put("proxied", false);

        if (type == DnsRecord.RecordType.MX) {
            body.put("priority", record.getPriority() != null ? record.getPriority() : 10);
        }

        try {
            webClientBuilder.build()
                    .put()
                    .uri("https://api.cloudflare.com/client/v4/zones/" + config.getZoneId() + "/dns_records/" + record.getExternalId())
                    .header("Authorization", "Bearer " + config.getApiToken())
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody.contains("81058") || responseBody.contains("already exists")) {
                log.info("Cloudflare record identical, skipping update: {} {}", record.getType(), record.getName());
                return;
            }
            log.error("Cloudflare API update error ({}): {}", e.getStatusCode(), responseBody);
            throw new RuntimeException("Cloudflare API update error: " + responseBody);
        }
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
