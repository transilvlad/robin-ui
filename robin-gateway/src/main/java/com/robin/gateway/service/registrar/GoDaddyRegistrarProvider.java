package com.robin.gateway.service.registrar;

import com.robin.gateway.model.Domain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoDaddyRegistrarProvider implements RegistrarProvider {

    private final com.robin.gateway.service.EncryptionService encryptionService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @lombok.Data
    private static class GoDaddyConfig {
        private String apiKey;
        private String apiSecret;
    }

    private GoDaddyConfig getConfig(String domainName, Domain domain) {
        if (domain == null || domain.getRegistrarProvider() == null) {
            return null;
        }
        try {
            String decrypted = encryptionService.decrypt(domain.getRegistrarProvider().getCredentials());
            return objectMapper.readValue(decrypted, GoDaddyConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse GoDaddy config", e);
            return null;
        }
    }

    @Override
    public DomainInfo getDomainDetails(String domainName) {
        log.info("Fetching GoDaddy details for {}", domainName);
        return new DomainInfo(java.time.LocalDate.now().plusYears(1), List.of("ns1.godaddy.com"), "ACTIVE");
    }

    @Override
    public void updateNameservers(String domainName, List<String> nameservers) {
        log.info("Updating GoDaddy nameservers for {}", domainName);
    }
}
