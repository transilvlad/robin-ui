package com.robin.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53domains.Route53DomainsClient;

@Configuration
public class AwsConfig {

    @Bean
    public Route53Client route53Client() {
        return Route53Client.builder()
                .region(Region.AWS_GLOBAL)
                .build();
    }

    @Bean
    public Route53DomainsClient route53DomainsClient() {
        return Route53DomainsClient.builder()
                .region(Region.US_EAST_1) // Route53 Domains is US-East-1 only for most operations
                .build();
    }
}
