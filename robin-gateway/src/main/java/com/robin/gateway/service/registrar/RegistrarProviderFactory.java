package com.robin.gateway.service.registrar;

import com.robin.gateway.model.Domain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrarProviderFactory {

    private final GoDaddyRegistrarProvider goDaddyRegistrarProvider;
    // Add others when implemented

    public RegistrarProvider getProvider(Domain.RegistrarProviderType type) {
        return switch (type) {
            case GODADDY -> goDaddyRegistrarProvider;
            case CLOUDFLARE -> new RegistrarProvider() {
                @Override
                public DomainInfo getDomainDetails(String domainName) {
                    return new DomainInfo(java.time.LocalDate.now().plusYears(1), java.util.List.of("ns1.cloudflare.com"), "ACTIVE");
                }
                @Override
                public void updateNameservers(String domainName, java.util.List<String> nameservers) {}
            };
            case AWS_ROUTE53 -> new RegistrarProvider() {
                @Override
                public DomainInfo getDomainDetails(String domainName) {
                    return new DomainInfo(java.time.LocalDate.now().plusYears(1), java.util.List.of("ns1.awsdns.com"), "ACTIVE");
                }
                @Override
                public void updateNameservers(String domainName, java.util.List<String> nameservers) {}
            };
            default -> new RegistrarProvider() {
                @Override
                public DomainInfo getDomainDetails(String domainName) {
                    return null;
                }

                @Override
                public void updateNameservers(String domainName, java.util.List<String> nameservers) {
                    // No-op
                }
            };
        };
    }
}
