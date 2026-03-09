package com.robin.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.integration.cloudflare.CloudflareApiClient;
import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.DnsProviderType;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.DomainDnsRecord;
import com.robin.gateway.model.MtaStsWorker;
import com.robin.gateway.model.MtaStsWorkerStatus;
import com.robin.gateway.repository.DnsProviderRepository;
import com.robin.gateway.repository.DomainDnsRecordRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.repository.MtaStsWorkerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MtaStsService reconciliation")
class MtaStsServiceTest {

    @Mock
    private MtaStsWorkerRepository mtaStsWorkerRepository;
    @Mock
    private DomainRepository domainRepository;
    @Mock
    private DnsProviderRepository dnsProviderRepository;
    @Mock
    private DomainDnsRecordRepository dnsRecordRepository;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private CloudflareApiClient cloudflareApiClient;
    @Mock
    private DnsResolverService dnsResolverService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MtaStsService mtaStsService;

    @Test
    @DisplayName("reconcileExistingInfrastructure should hydrate local MTA-STS state when worker and TXT record already exist")
    void reconcileExistingInfrastructureHydratesState() throws Exception {
        Domain domain = Domain.builder()
                .id(7L)
                .domain("example.com")
                .dnsProviderId(11L)
                .build();
        DnsProvider provider = DnsProvider.builder()
                .id(11L)
                .type(DnsProviderType.CLOUDFLARE)
                .credentials("encrypted-json")
                .build();
        JsonNode creds = new ObjectMapper().readTree("{\"apiToken\":\"cf-token\",\"accountId\":\"acc-1\"}");
        JsonNode records = new ObjectMapper().readTree("""
                [
                  {"id":"txt-rec-1","type":"TXT","name":"_mta-sts.example.com","content":"\\"v=STSv1; id=171234\\""}
                ]
                """);

        when(domainRepository.findById(7L)).thenReturn(Optional.of(domain));
        when(dnsProviderRepository.findById(11L)).thenReturn(Optional.of(provider));
        when(encryptionService.decrypt("encrypted-json")).thenReturn("{\"apiToken\":\"cf-token\",\"accountId\":\"acc-1\"}");
        when(objectMapper.readTree("{\"apiToken\":\"cf-token\",\"accountId\":\"acc-1\"}")).thenReturn(creds);
        when(cloudflareApiClient.getZoneId("example.com", "cf-token")).thenReturn(Mono.just("zone-1"));
        when(cloudflareApiClient.getWorkerScriptId("acc-1", "mta-sts-example-com", "cf-token")).thenReturn(Mono.just("worker-1"));
        when(cloudflareApiClient.listDnsRecords("zone-1", "cf-token")).thenReturn(Mono.just(records));
        when(mtaStsWorkerRepository.findByDomainId(7L)).thenReturn(Optional.empty());
        when(mtaStsWorkerRepository.save(any(MtaStsWorker.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dnsRecordRepository.findByDomainIdAndRecordType(7L, "TXT")).thenReturn(List.of());
        when(dnsRecordRepository.save(any(DomainDnsRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Boolean reconciled = mtaStsService.reconcileExistingInfrastructure(7L).block();

        assertThat(reconciled).isTrue();

        ArgumentCaptor<MtaStsWorker> workerCaptor = ArgumentCaptor.forClass(MtaStsWorker.class);
        verify(mtaStsWorkerRepository).save(workerCaptor.capture());
        MtaStsWorker worker = workerCaptor.getValue();
        assertThat(worker.getDomainId()).isEqualTo(7L);
        assertThat(worker.getWorkerName()).isEqualTo("mta-sts-example-com");
        assertThat(worker.getWorkerId()).isEqualTo("worker-1");
        assertThat(worker.getStatus()).isEqualTo(MtaStsWorkerStatus.DEPLOYED);
        assertThat(worker.getPolicyVersion()).isEqualTo("171234");

        ArgumentCaptor<DomainDnsRecord> txtCaptor = ArgumentCaptor.forClass(DomainDnsRecord.class);
        verify(dnsRecordRepository).save(txtCaptor.capture());
        DomainDnsRecord txt = txtCaptor.getValue();
        assertThat(txt.getDomainId()).isEqualTo(7L);
        assertThat(txt.getName()).isEqualTo("_mta-sts.example.com");
        assertThat(txt.getValue()).isEqualTo("v=STSv1; id=171234");
        assertThat(txt.getProviderRecordId()).isEqualTo("txt-rec-1");
    }

    @Test
    @DisplayName("reconcileExistingInfrastructure should return false when worker script is missing, even if TXT exists")
    void reconcileExistingInfrastructureReturnsFalseWhenNoInfraFound() throws Exception {
        Domain domain = Domain.builder()
                .id(8L)
                .domain("empty.example.com")
                .dnsProviderId(21L)
                .build();
        DnsProvider provider = DnsProvider.builder()
                .id(21L)
                .type(DnsProviderType.CLOUDFLARE)
                .credentials("encrypted-json")
                .build();
        JsonNode creds = new ObjectMapper().readTree("{\"apiToken\":\"cf-token\",\"accountId\":\"acc-1\"}");
        JsonNode records = new ObjectMapper().readTree("""
                [
                  {"id":"txt-rec-2","type":"TXT","name":"_mta-sts.empty.example.com","content":"\\"v=STSv1; id=998877\\""}
                ]
                """);

        when(domainRepository.findById(8L)).thenReturn(Optional.of(domain));
        when(dnsProviderRepository.findById(21L)).thenReturn(Optional.of(provider));
        when(encryptionService.decrypt("encrypted-json")).thenReturn("{\"apiToken\":\"cf-token\",\"accountId\":\"acc-1\"}");
        when(objectMapper.readTree("{\"apiToken\":\"cf-token\",\"accountId\":\"acc-1\"}")).thenReturn(creds);
        when(cloudflareApiClient.getZoneId("empty.example.com", "cf-token")).thenReturn(Mono.just("zone-2"));
        when(cloudflareApiClient.getWorkerScriptId("acc-1", "mta-sts-empty-example-com", "cf-token")).thenReturn(Mono.empty());
        when(cloudflareApiClient.listDnsRecords("zone-2", "cf-token")).thenReturn(Mono.just(records));

        Boolean reconciled = mtaStsService.reconcileExistingInfrastructure(8L).block();

        assertThat(reconciled).isFalse();
        verify(mtaStsWorkerRepository, never()).save(any(MtaStsWorker.class));
        verify(dnsRecordRepository, never()).save(any(DomainDnsRecord.class));
    }
}
