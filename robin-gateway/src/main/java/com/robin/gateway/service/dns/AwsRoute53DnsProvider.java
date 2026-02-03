package com.robin.gateway.service.dns;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.service.EncryptionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.route53.Route53Client;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwsRoute53DnsProvider implements DnsProvider {

    private final Route53Client route53Client;
    private final EncryptionService encryptionService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Data
    private static class AwsConfig {
        private String accessKey;
        private String secretKey;
        private String region;
    }

    private AwsConfig getConfig(Domain domain) {
        if (domain.getDnsProvider() == null) {
            throw new RuntimeException("AWS provider not configured for this domain");
        }
        try {
            String decrypted = encryptionService.decrypt(domain.getDnsProvider().getCredentials());
            return objectMapper.readValue(decrypted, AwsConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse AWS config", e);
            throw new RuntimeException("Invalid AWS configuration");
        }
    }

    @Override
    public List<DnsRecord> listRecords(Domain domain) {
        log.info("Listing AWS Route53 DNS records for domain: {}", domain.getDomain());
        // Logic to ListResourceRecordSets
        return List.of();
    }

    @Override
    public void createRecord(Domain domain, DnsRecord record) {
        log.info("Creating AWS Route53 DNS record: {} {}", record.getType(), record.getName());
        // Logic to ChangeResourceRecordSets (UPSERT)
    }

    @Override
    public void updateRecord(Domain domain, DnsRecord record) {
        createRecord(domain, record);
    }

    @Override
    public void deleteRecord(Domain domain, String externalId) {
        log.info("Deleting AWS Route53 DNS record: {}", externalId);
    }

    @Override
    public void enableDnssec(Domain domain) {
        log.info("Enabling DNSSEC on AWS Route53 for: {}", domain.getDomain());
    }

    @Override
    public void disableDnssec(Domain domain) {
        log.info("Disabling DNSSEC on AWS Route53 for: {}", domain.getDomain());
    }

    @Override
    public List<DnsRecord> getDsRecords(Domain domain) {
        return List.of();
    }
}
