package com.robin.gateway.integration.aws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.route53.Route53AsyncClient;
import software.amazon.awssdk.services.route53.model.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class Route53ApiClient {

    private Route53AsyncClient createClient(String accessKey, String secretKey, String regionStr) {
        Region region = Region.of(regionStr != null ? regionStr : "us-east-1");
        return Route53AsyncClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    public CompletableFuture<String> getHostedZoneId(String domain, String accessKey, String secretKey, String region) {
        Route53AsyncClient client = createClient(accessKey, secretKey, region);
        ListHostedZonesByNameRequest request = ListHostedZonesByNameRequest.builder()
                .dnsName(domain)
                .maxItems("1")
                .build();

        return client.listHostedZonesByName(request)
                .thenApply(response -> {
                    if (response.hasHostedZones() && !response.hostedZones().isEmpty()) {
                        HostedZone zone = response.hostedZones().get(0);
                        // Make sure the name matches the domain exactly (with or without trailing dot)
                        if (zone.name().equals(domain + ".") || zone.name().equals(domain)) {
                            // Route53 returns zone ID as /hostedzone/Z1234567890
                            String id = zone.id();
                            return id.contains("/") ? id.substring(id.lastIndexOf("/") + 1) : id;
                        }
                    }
                    throw new RuntimeException("Hosted zone not found for domain: " + domain);
                })
                .whenComplete((res, ex) -> client.close());
    }

    public CompletableFuture<ChangeInfo> changeResourceRecordSets(String zoneId, List<Change> changes, String accessKey, String secretKey, String region) {
        Route53AsyncClient client = createClient(accessKey, secretKey, region);

        ChangeBatch changeBatch = ChangeBatch.builder()
                .changes(changes)
                .build();

        ChangeResourceRecordSetsRequest request = ChangeResourceRecordSetsRequest.builder()
                .hostedZoneId(zoneId)
                .changeBatch(changeBatch)
                .build();

        return client.changeResourceRecordSets(request)
                .thenApply(ChangeResourceRecordSetsResponse::changeInfo)
                .whenComplete((res, ex) -> client.close());
    }

    public CompletableFuture<List<ResourceRecordSet>> listResourceRecordSets(String zoneId, String accessKey, String secretKey, String region) {
        Route53AsyncClient client = createClient(accessKey, secretKey, region);

        ListResourceRecordSetsRequest request = ListResourceRecordSetsRequest.builder()
                .hostedZoneId(zoneId)
                .build();

        return client.listResourceRecordSets(request)
                .thenApply(ListResourceRecordSetsResponse::resourceRecordSets)
                .whenComplete((res, ex) -> client.close());
    }
}
