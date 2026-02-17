package com.robin.gateway.service.dns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.ProviderConfig;
import com.robin.gateway.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("CloudflareDnsProvider Tests")
class CloudflareDnsProviderTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private EncryptionService encryptionService;

    private CloudflareDnsProvider provider;
    private Domain testDomain;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        provider = new CloudflareDnsProvider(webClientBuilder, encryptionService, objectMapper);

        ProviderConfig config = ProviderConfig.builder()
                .id(1L)
                .type(ProviderConfig.ProviderType.CLOUDFLARE)
                .credentials("encrypted-creds")
                .build();

        testDomain = Domain.builder()
                .domain("example.com")
                .dnsProvider(config)
                .build();

        when(encryptionService.decrypt("encrypted-creds")).thenReturn("{\"apiToken\":\"test-token\",\"zoneId\":\"test-zone\"}");
        when(webClientBuilder.build()).thenReturn(webClient);
    }

    @Test
    @DisplayName("Should list records correctly")
    void testListRecords() {
        CloudflareDnsProvider.CloudflareResult result = new CloudflareDnsProvider.CloudflareResult();
        result.setId("rec-1");
        result.setType("A");
        result.setName("mail.example.com");
        result.setContent("1.2.3.4");
        result.setTtl(3600);

        CloudflareDnsProvider.CloudflareResponse response = new CloudflareDnsProvider.CloudflareResponse();
        response.setSuccess(true);
        response.setResult(List.of(result));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(CloudflareDnsProvider.CloudflareResponse.class)).thenReturn(Mono.just(response));

        List<DnsRecord> records = provider.listRecords(testDomain);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getName()).isEqualTo("mail.example.com");
        assertThat(records.get(0).getContent()).isEqualTo("1.2.3.4");
        assertThat(records.get(0).getExternalId()).isEqualTo("rec-1");
    }

    @Test
    @DisplayName("Should create record successfully")
    void testCreateRecord() {
        DnsRecord record = DnsRecord.builder()
                .type(DnsRecord.RecordType.A)
                .name("mail")
                .content("1.2.3.4")
                .ttl(3600)
                .build();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());

        provider.createRecord(testDomain, record);
    }
}
