package com.robin.gateway.service.dns;

import com.robin.gateway.model.Domain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DnsProviderFactory {

    private final CloudflareDnsProvider cloudflareDnsProvider;
    private final AwsRoute53DnsProvider awsRoute53DnsProvider;
    private final ManualDnsProvider manualDnsProvider;

    public DnsProvider getProvider(Domain.DnsProviderType type) {
        return switch (type) {
            case CLOUDFLARE -> cloudflareDnsProvider;
            case AWS_ROUTE53 -> awsRoute53DnsProvider;
            case MANUAL -> manualDnsProvider;
        };
    }
}
