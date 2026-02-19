package com.robin.gateway.service;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DnsRecordRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.service.dns.DnsProvider;
import com.robin.gateway.service.dns.DnsProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("DomainSyncService Tests")
class DomainSyncServiceTest {

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private DnsRecordRepository dnsRecordRepository;

    @Mock
    private DnsRecordGenerator dnsRecordGenerator;

    @Mock
    private DnsProviderFactory dnsProviderFactory;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private DnsProvider dnsProvider;

    private DomainSyncService domainSyncService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        domainSyncService = new DomainSyncService(
                domainRepository,
                dnsRecordRepository,
                dnsRecordGenerator,
                dnsProviderFactory,
                transactionManager
        );

        // Setup transaction manager to execute callback immediately
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any(TransactionStatus.class));
    }

    @Test
    @DisplayName("Should throw exception when domain not found")
    void testSyncDomainNotFound() {
        Long domainId = 999L;
        when(domainRepository.findById(domainId)).thenReturn(Optional.empty());

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Domain not found"))
                .verify();
    }

    @Test
    @DisplayName("Should skip sync for MANUAL provider type")
    void testSyncDomainManualProvider() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.MANUAL)
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(new ArrayList<>());

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        // Should not call provider factory for MANUAL domains
        verify(dnsProviderFactory, never()).getProvider(any());
    }

    @Test
    @DisplayName("Should create missing remote records")
    void testSyncDomainCreateMissingRecords() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        // Local record that doesn't exist remotely
        DnsRecord localRecord = DnsRecord.builder()
                .id(1L)
                .name("mail")
                .type(DnsRecord.RecordType.A)
                .content("1.2.3.4")
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(List.of(localRecord));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(new ArrayList<>()); // No remote records
        when(dnsRecordRepository.saveAll(any())).thenReturn(List.of(localRecord));

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        verify(dnsProvider).createRecord(domain, localRecord);
        verify(dnsRecordRepository).saveAll(any());
    }

    @Test
    @DisplayName("Should update existing remote records when content differs")
    void testSyncDomainUpdateRecords() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        // Local record with new content
        DnsRecord localRecord = DnsRecord.builder()
                .id(1L)
                .name("mail.test.com")
                .type(DnsRecord.RecordType.A)
                .content("1.2.3.5") // New IP
                .externalId("ext-123")
                .build();

        // Remote record with old content
        DnsRecord remoteRecord = DnsRecord.builder()
                .name("mail.test.com")
                .type(DnsRecord.RecordType.A)
                .content("1.2.3.4") // Old IP
                .externalId("ext-123")
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(List.of(localRecord));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(List.of(remoteRecord));
        when(dnsRecordRepository.saveAll(any())).thenReturn(List.of(localRecord));

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        verify(dnsProvider).updateRecord(domain, localRecord);
        verify(dnsProvider, never()).createRecord(any(), any());
    }

    @Test
    @DisplayName("Should delete unmanaged remote records")
    void testSyncDomainDeleteUnmanagedRemoteRecords() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        // No local records (everything deleted from Robin)
        List<DnsRecord> localRecords = new ArrayList<>();

        // Remote record that is Robin-managed but no longer in local DB
        DnsRecord remoteRecord = DnsRecord.builder()
                .name("mail.test.com")
                .type(DnsRecord.RecordType.A)
                .content("1.2.3.4")
                .externalId("ext-123")
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(localRecords);
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(List.of(remoteRecord));
        when(dnsRecordRepository.saveAll(any())).thenReturn(localRecords);

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        verify(dnsProvider).deleteRecord(domain, "ext-123");
    }

    @Test
    @DisplayName("Should not delete unmanaged records")
    void testSyncDomainPreserveUnmanagedRecords() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        // Remote record that Robin doesn't manage (e.g., www)
        DnsRecord unmanagedRemote = DnsRecord.builder()
                .name("www.test.com")
                .type(DnsRecord.RecordType.A)
                .content("1.2.3.4")
                .externalId("ext-456")
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(new ArrayList<>());
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(List.of(unmanagedRemote));
        when(dnsRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        // Should NOT delete unmanaged record
        verify(dnsProvider, never()).deleteRecord(any(), anyString());
    }

    @Test
    @DisplayName("Should update sync status after successful sync")
    void testSyncDomainUpdatesSyncStatus() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        DnsRecord localRecord = DnsRecord.builder()
                .id(1L)
                .name("@")
                .type(DnsRecord.RecordType.MX)
                .content("mail.test.com")
                .priority(10)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        DnsRecord remoteRecord = DnsRecord.builder()
                .name("test.com")
                .type(DnsRecord.RecordType.MX)
                .content("mail.test.com")
                .priority(10)
                .externalId("ext-mx-1")
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(List.of(localRecord));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(List.of(remoteRecord));
        when(dnsRecordRepository.saveAll(any())).thenReturn(List.of(localRecord));

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        // Verify sync status was updated
        assertThat(localRecord.getSyncStatus()).isEqualTo(DnsRecord.SyncStatus.SYNCED);
        assertThat(localRecord.getLastSyncedAt()).isNotNull();
        assertThat(localRecord.getExternalId()).isEqualTo("ext-mx-1");
    }

    @Test
    @DisplayName("Should handle provider sync failure")
    void testSyncDomainProviderFailure() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(new ArrayList<>());
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenThrow(new RuntimeException("API error"));

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Provider sync failed"))
                .verify();
    }

    @Test
    @DisplayName("Should match records by external ID")
    void testSyncDomainMatchByExternalId() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        DnsRecord localRecord = DnsRecord.builder()
                .id(1L)
                .name("@")
                .type(DnsRecord.RecordType.A)
                .content("1.2.3.4")
                .externalId("ext-123")
                .build();

        // Remote record with same external ID (even if name format differs)
        DnsRecord remoteRecord = DnsRecord.builder()
                .name("test.com")
                .type(DnsRecord.RecordType.A)
                .content("1.2.3.4")
                .externalId("ext-123")
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(List.of(localRecord));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(List.of(remoteRecord));
        when(dnsRecordRepository.saveAll(any())).thenReturn(List.of(localRecord));

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        // Should not create or update (perfect match)
        verify(dnsProvider, never()).createRecord(any(), any());
        verify(dnsProvider, never()).updateRecord(any(), any());
    }

    @Test
    @DisplayName("Should handle TXT record quote differences")
    void testSyncDomainTxtQuoteInsensitive() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        DnsRecord localRecord = DnsRecord.builder()
                .id(1L)
                .name("@")
                .type(DnsRecord.RecordType.TXT)
                .content("v=spf1 include:_spf.google.com ~all")
                .externalId("ext-spf")
                .build();

        // Remote has quotes
        DnsRecord remoteRecord = DnsRecord.builder()
                .name("test.com")
                .type(DnsRecord.RecordType.TXT)
                .content("\"v=spf1 include:_spf.google.com ~all\"")
                .externalId("ext-spf")
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(List.of(localRecord));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(List.of(remoteRecord));
        when(dnsRecordRepository.saveAll(any())).thenReturn(List.of(localRecord));

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        // Should recognize as same content despite quotes
        verify(dnsProvider, never()).updateRecord(any(), any());
    }

    @Test
    @DisplayName("Should update when priority differs for MX records")
    void testSyncDomainUpdateOnPriorityChange() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        DnsRecord localRecord = DnsRecord.builder()
                .id(1L)
                .name("@")
                .type(DnsRecord.RecordType.MX)
                .content("mail.test.com")
                .priority(20) // Changed priority
                .externalId("ext-mx")
                .build();

        DnsRecord remoteRecord = DnsRecord.builder()
                .name("test.com")
                .type(DnsRecord.RecordType.MX)
                .content("mail.test.com")
                .priority(10) // Old priority
                .externalId("ext-mx")
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(List.of(localRecord));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(List.of(remoteRecord));
        when(dnsRecordRepository.saveAll(any())).thenReturn(List.of(localRecord));

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        // Should update due to priority difference
        verify(dnsProvider).updateRecord(domain, localRecord);
    }

    @Test
    @DisplayName("Should save all records after sync")
    void testSyncDomainSavesRecords() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        List<DnsRecord> localRecords = List.of(
                DnsRecord.builder().id(1L).name("@").type(DnsRecord.RecordType.MX).content("mail.test.com").priority(10).build(),
                DnsRecord.builder().id(2L).name("mail").type(DnsRecord.RecordType.A).content("1.2.3.4").build()
        );

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(localRecords);
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(new ArrayList<>());
        when(dnsRecordRepository.saveAll(anyList())).thenReturn(localRecords);

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        verify(dnsRecordRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should handle root domain name variations (@, domain.com, domain.com.)")
    void testSyncDomainRootNameVariations() {
        Long domainId = 1L;
        Domain domain = Domain.builder()
                .id(domainId)
                .domain("test.com")
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        // Local uses @
        DnsRecord localRecord = DnsRecord.builder()
                .id(1L)
                .name("@")
                .type(DnsRecord.RecordType.A)
                .content("1.2.3.4")
                .build();

        // Remote uses full domain
        DnsRecord remoteRecord = DnsRecord.builder()
                .name("test.com")
                .type(DnsRecord.RecordType.A)
                .content("1.2.3.4")
                .externalId("ext-root")
                .build();

        when(domainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(dnsRecordRepository.findByDomain_Id(domainId)).thenReturn(List.of(localRecord));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(domain)).thenReturn(List.of(remoteRecord));
        when(dnsRecordRepository.saveAll(any())).thenReturn(List.of(localRecord));

        StepVerifier.create(domainSyncService.syncDomain(domainId))
                .verifyComplete();

        // Should recognize as same record
        verify(dnsProvider, never()).createRecord(any(), any());
        verify(dnsProvider, never()).updateRecord(any(), any());
        assertThat(localRecord.getExternalId()).isEqualTo("ext-root");
    }
}
