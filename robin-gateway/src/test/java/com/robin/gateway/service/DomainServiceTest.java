package com.robin.gateway.service;

import com.robin.gateway.model.dto.InitialRecordRequest;
import com.robin.gateway.model.Alias;
import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.ProviderConfig;
import com.robin.gateway.repository.AliasRepository;
import com.robin.gateway.repository.DnsRecordRepository;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.repository.ProviderConfigRepository;
import com.robin.gateway.service.dns.DnsProvider;
import com.robin.gateway.service.dns.DnsProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for DomainService.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Domain CRUD operations</li>
 *   <li>DNSSEC management</li>
 *   <li>Alias management</li>
 *   <li>Error handling</li>
 *   <li>Reactive operations</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DomainServiceTest {

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private AliasRepository aliasRepository;

    @Mock
    private ProviderConfigRepository providerConfigRepository;

    @Mock
    private DnsRecordRepository dnsRecordRepository;

    @Mock
    private DkimService dkimService;

    @Mock
    private DnsRecordGenerator dnsRecordGenerator;

    @Mock
    private ConfigurationService configService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private DnsProviderFactory dnsProviderFactory;

    @InjectMocks
    private DomainService domainService;

    private Domain testDomain;
    private Alias testAlias;
    private ProviderConfig dnsProviderConfig;
    private DnsProvider mockDnsProvider;

    @BeforeEach
    void setUp() {
        dnsProviderConfig = ProviderConfig.builder()
                .id(1L)
                .name("Cloudflare DNS")
                .type(ProviderConfig.ProviderType.CLOUDFLARE)
                .build();

        testDomain = Domain.builder()
                .id(1L)
                .domain("example.com")
                .dnsProvider(dnsProviderConfig)
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .dnssecEnabled(false)
                .mtaStsEnabled(true)
                .mtaStsMode(Domain.MtaStsMode.ENFORCE)
                .dmarcPolicy("quarantine")
                .dmarcReportingEmail("dmarc@example.com")
                .spfIncludes("include:_spf.example.com")
                .build();

        testAlias = Alias.builder()
                .id(1L)
                .source("alias@example.com")
                .destination("user@example.com")
                .build();

        mockDnsProvider = mock(DnsProvider.class);
    }

    // ==================== Domain CRUD Tests ====================

    @Test
    @DisplayName("Should retrieve all domains with pagination successfully")
    void testGetAllDomainsSuccess() {
        // Arrange
        Domain domain2 = Domain.builder()
                .id(2L)
                .domain("test.com")
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Domain> page = new PageImpl<>(Arrays.asList(testDomain, domain2), pageable, 2);

        when(domainRepository.findAll(pageable)).thenReturn(page);

        // Act & Assert
        StepVerifier.create(domainService.getAllDomains(pageable))
                .expectNext(page)
                .verifyComplete();

        verify(domainRepository).findAll(pageable);
    }

    @Test
    @DisplayName("Should return empty page when no domains exist")
    void testGetAllDomainsEmpty() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Domain> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(domainRepository.findAll(pageable)).thenReturn(emptyPage);

        // Act & Assert
        StepVerifier.create(domainService.getAllDomains(pageable))
                .expectNext(emptyPage)
                .verifyComplete();

        verify(domainRepository).findAll(pageable);
    }

    @Test
    @DisplayName("Should retrieve domain by ID successfully")
    void testGetDomainByIdSuccess() {
        // Arrange
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));

        // Act & Assert
        StepVerifier.create(domainService.getDomainById(1L))
                .expectNext(testDomain)
                .verifyComplete();

        verify(domainRepository).findById(1L);
    }

    @Test
    @DisplayName("Should return error when domain not found by ID")
    void testGetDomainByIdNotFound() {
        // Arrange
        when(domainRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(domainService.getDomainById(999L))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Domain not found: 999"))
                .verify();

        verify(domainRepository).findById(999L);
    }

    @Test
    @DisplayName("Should retrieve domain by name successfully")
    void testGetDomainByNameSuccess() {
        // Arrange
        when(domainRepository.findByDomain("example.com")).thenReturn(Optional.of(testDomain));

        // Act & Assert
        StepVerifier.create(domainService.getDomainByName("example.com"))
                .expectNext(Optional.of(testDomain))
                .verifyComplete();

        verify(domainRepository).findByDomain("example.com");
    }

    @Test
    @DisplayName("Should return empty optional when domain not found by name")
    void testGetDomainByNameNotFound() {
        // Arrange
        when(domainRepository.findByDomain("nonexistent.com")).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(domainService.getDomainByName("nonexistent.com"))
                .expectNext(Optional.empty())
                .verifyComplete();

        verify(domainRepository).findByDomain("nonexistent.com");
    }

    @Test
    @DisplayName("Should create domain successfully with minimal configuration")
    void testCreateDomainSuccess() {
        // Arrange
        Domain newDomain = Domain.builder()
                .id(2L)
                .domain("newdomain.com")
                .build();

        List<DnsRecord> generatedRecords = Arrays.asList(
                DnsRecord.builder().type(DnsRecord.RecordType.A).name("@").content("192.168.1.1").build(),
                DnsRecord.builder().type(DnsRecord.RecordType.MX).name("@").content("mail.newdomain.com").priority(10).build()
        );

        when(domainRepository.existsByDomain("newdomain.com")).thenReturn(false);
        when(configService.getConfig("email_reporting")).thenReturn(Mono.just(new HashMap<>()));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            when(domainRepository.save(any(Domain.class))).thenReturn(newDomain);
            when(dnsRecordGenerator.generateExpectedRecords(any())).thenReturn(generatedRecords);
            when(dnsRecordRepository.saveAll(anyList())).thenReturn(generatedRecords);
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
        });

        // Act & Assert
        StepVerifier.create(domainService.createDomain("newdomain.com", null, null, null, null, null))
                .expectNext(newDomain)
                .verifyComplete();

        verify(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("Should reject creating domain with existing name")
    void testCreateDomainDuplicate() {
        // Arrange
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            when(domainRepository.existsByDomain("example.com")).thenReturn(true);
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
        });

        // Act & Assert
        StepVerifier.create(domainService.createDomain("example.com", null, null, null, null, null))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().contains("Domain already exists"))
                .verify();
    }

    @Test
    @DisplayName("Should create domain with DNS provider configuration")
    void testCreateDomainWithDnsProvider() {
        // Arrange
        Domain domainWithProvider = Domain.builder()
                .id(3L)
                .domain("provider.com")
                .dnsProvider(dnsProviderConfig)
                .dnsProviderType(Domain.DnsProviderType.CLOUDFLARE)
                .build();

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            when(domainRepository.existsByDomain("provider.com")).thenReturn(false);
            when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(dnsProviderConfig));
            when(configService.getConfig("email_reporting")).thenReturn(Mono.just(new HashMap<>()));
            when(domainRepository.save(any(Domain.class))).thenReturn(domainWithProvider);
            when(dnsRecordGenerator.generateExpectedRecords(any())).thenReturn(Collections.emptyList());
            when(dnsRecordRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
        });

        // Act & Assert
        StepVerifier.create(domainService.createDomain("provider.com", 1L, null, null, null, null))
                .expectNext(domainWithProvider)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should create domain with initial DNS records")
    void testCreateDomainWithInitialRecords() {
        // Arrange
        InitialRecordRequest record1 = InitialRecordRequest.builder()
                .type(DnsRecord.RecordType.A)
                .name("@")
                .content("192.168.1.1")
                .ttl(3600)
                .build();

        List<InitialRecordRequest> initialRecords = Arrays.asList(record1);

        Domain savedDomain = Domain.builder()
                .id(4L)
                .domain("records.com")
                .build();

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            when(domainRepository.existsByDomain("records.com")).thenReturn(false);
            when(configService.getConfig("email_reporting")).thenReturn(Mono.just(new HashMap<>()));
            when(domainRepository.save(any(Domain.class))).thenReturn(savedDomain);
            when(dnsRecordRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
        });

        // Act & Assert
        StepVerifier.create(domainService.createDomain("records.com", null, null, null, null, initialRecords))
                .expectNext(savedDomain)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update domain successfully")
    void testUpdateDomainSuccess() {
        // Arrange
        Domain updates = Domain.builder()
                .dmarcPolicy("reject")
                .spfIncludes("include:_spf.newserver.com")
                .mtaStsMode(Domain.MtaStsMode.TESTING)
                .build();

        Domain updatedDomain = Domain.builder()
                .id(1L)
                .domain("example.com")
                .dmarcPolicy("reject")
                .spfIncludes("include:_spf.newserver.com")
                .mtaStsMode(Domain.MtaStsMode.TESTING)
                .build();

        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(domainRepository.save(any(Domain.class))).thenReturn(updatedDomain);

        // Act & Assert
        StepVerifier.create(domainService.updateDomain(1L, updates))
                .expectNext(updatedDomain)
                .verifyComplete();

        verify(domainRepository).findById(1L);
        verify(domainRepository).save(any(Domain.class));
    }

    @Test
    @DisplayName("Should reject updating non-existent domain")
    void testUpdateDomainNotFound() {
        // Arrange
        Domain updates = Domain.builder()
                .dmarcPolicy("reject")
                .build();

        when(domainRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(domainService.updateDomain(999L, updates))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Domain not found: 999"))
                .verify();

        verify(domainRepository).findById(999L);
        verify(domainRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update domain with provider changes")
    void testUpdateDomainWithProviderChange() {
        // Arrange
        ProviderConfig newProvider = ProviderConfig.builder()
                .id(2L)
                .name("AWS Route53")
                .type(ProviderConfig.ProviderType.AWS_ROUTE53)
                .build();

        Domain updates = Domain.builder()
                .dnsProvider(newProvider)
                .dnsProviderType(Domain.DnsProviderType.AWS_ROUTE53)
                .build();

        Domain updatedDomain = Domain.builder()
                .id(1L)
                .domain("example.com")
                .dnsProvider(newProvider)
                .dnsProviderType(Domain.DnsProviderType.AWS_ROUTE53)
                .build();

        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(providerConfigRepository.findById(2L)).thenReturn(Optional.of(newProvider));
        when(domainRepository.save(any(Domain.class))).thenReturn(updatedDomain);

        // Act & Assert
        StepVerifier.create(domainService.updateDomain(1L, updates))
                .expectNext(updatedDomain)
                .verifyComplete();

        verify(providerConfigRepository).findById(2L);
    }

    @Test
    @DisplayName("Should delete domain successfully")
    void testDeleteDomainSuccess() {
        // Arrange
        when(domainRepository.existsById(1L)).thenReturn(true);
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(aliasRepository.findBySource("example.com%")).thenReturn(Collections.emptyList());
        doNothing().when(domainRepository).deleteById(1L);

        // Act & Assert
        StepVerifier.create(domainService.deleteDomain(1L))
                .verifyComplete();

        verify(domainRepository).existsById(1L);
        verify(domainRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Should delete domain and associated aliases")
    void testDeleteDomainWithAliases() {
        // Arrange
        List<Alias> aliases = Arrays.asList(
                Alias.builder().id(1L).source("alias1@example.com").destination("user1@example.com").build(),
                Alias.builder().id(2L).source("alias2@example.com").destination("user2@example.com").build()
        );

        when(domainRepository.existsById(1L)).thenReturn(true);
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(aliasRepository.findBySource("example.com%")).thenReturn(aliases);
        doNothing().when(aliasRepository).deleteAll(aliases);
        doNothing().when(domainRepository).deleteById(1L);

        // Act & Assert
        StepVerifier.create(domainService.deleteDomain(1L))
                .verifyComplete();

        verify(aliasRepository).deleteAll(aliases);
        verify(domainRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Should reject deleting non-existent domain")
    void testDeleteDomainNotFound() {
        // Arrange
        when(domainRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        StepVerifier.create(domainService.deleteDomain(999L))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Domain not found: 999"))
                .verify();

        verify(domainRepository).existsById(999L);
        verify(domainRepository, never()).deleteById(any());
    }

    // ==================== DNSSEC Tests ====================

    @Test
    @DisplayName("Should get DNSSEC status successfully")
    void testGetDnssecStatusSuccess() {
        // Arrange
        List<DnsRecord> dsRecords = Arrays.asList(
                DnsRecord.builder().type(DnsRecord.RecordType.DS).content("12345 8 2 ABC123...").build()
        );

        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(mockDnsProvider);
        when(mockDnsProvider.getDsRecords(testDomain)).thenReturn(dsRecords);

        // Act & Assert
        StepVerifier.create(domainService.getDnssecStatus(1L))
                .expectNext(dsRecords)
                .verifyComplete();

        verify(dnsProviderFactory).getProvider(Domain.DnsProviderType.CLOUDFLARE);
        verify(mockDnsProvider).getDsRecords(testDomain);
    }

    @Test
    @DisplayName("Should return empty list for manual DNSSEC status")
    void testGetDnssecStatusManual() {
        // Arrange
        Domain manualDomain = Domain.builder()
                .id(2L)
                .domain("manual.com")
                .dnsProviderType(Domain.DnsProviderType.MANUAL)
                .build();

        when(domainRepository.findById(2L)).thenReturn(Optional.of(manualDomain));

        // Act & Assert
        StepVerifier.create(domainService.getDnssecStatus(2L))
                .expectNext(Collections.emptyList())
                .verifyComplete();

        verify(dnsProviderFactory, never()).getProvider(any());
    }

    @Test
    @DisplayName("Should enable DNSSEC successfully")
    void testEnableDnssecSuccess() {
        // Arrange
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(mockDnsProvider);
        doNothing().when(mockDnsProvider).enableDnssec(testDomain);
        when(domainRepository.save(any(Domain.class))).thenReturn(testDomain);

        // Act & Assert
        StepVerifier.create(domainService.enableDnssec(1L))
                .verifyComplete();

        verify(mockDnsProvider).enableDnssec(testDomain);
        verify(domainRepository).save(argThat(domain -> domain.getDnssecEnabled()));
    }

    @Test
    @DisplayName("Should enable DNSSEC for manual domain without provider call")
    void testEnableDnssecManual() {
        // Arrange
        Domain manualDomain = Domain.builder()
                .id(2L)
                .domain("manual.com")
                .dnsProviderType(Domain.DnsProviderType.MANUAL)
                .dnssecEnabled(false)
                .build();

        when(domainRepository.findById(2L)).thenReturn(Optional.of(manualDomain));
        when(domainRepository.save(any(Domain.class))).thenReturn(manualDomain);

        // Act & Assert
        StepVerifier.create(domainService.enableDnssec(2L))
                .verifyComplete();

        verify(dnsProviderFactory, never()).getProvider(any());
        verify(domainRepository).save(argThat(domain -> domain.getDnssecEnabled()));
    }

    @Test
    @DisplayName("Should disable DNSSEC successfully")
    void testDisableDnssecSuccess() {
        // Arrange
        testDomain.setDnssecEnabled(true);

        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(mockDnsProvider);
        doNothing().when(mockDnsProvider).disableDnssec(testDomain);
        when(domainRepository.save(any(Domain.class))).thenReturn(testDomain);

        // Act & Assert
        StepVerifier.create(domainService.disableDnssec(1L))
                .verifyComplete();

        verify(mockDnsProvider).disableDnssec(testDomain);
        verify(domainRepository).save(argThat(domain -> !domain.getDnssecEnabled()));
    }

    @Test
    @DisplayName("Should handle DNSSEC error on non-existent domain")
    void testDnssecOperationDomainNotFound() {
        // Arrange
        when(domainRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(domainService.enableDnssec(999L))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Domain not found: 999"))
                .verify();

        verify(domainRepository, never()).save(any());
    }

    // ==================== Alias Tests ====================

    @Test
    @DisplayName("Should get all aliases for domain successfully")
    void testGetAliasesByDomainSuccess() {
        // Arrange
        List<Alias> aliases = Arrays.asList(
                Alias.builder().id(1L).source("alias1@example.com").destination("user1@example.com").build(),
                Alias.builder().id(2L).source("alias2@example.com").destination("user2@example.com").build()
        );

        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(aliasRepository.findBySource("example.com%")).thenReturn(aliases);

        // Act & Assert
        StepVerifier.create(domainService.getAliasesByDomain(1L))
                .expectNext(aliases)
                .verifyComplete();

        verify(aliasRepository).findBySource("example.com%");
    }

    @Test
    @DisplayName("Should get all aliases with pagination successfully")
    void testGetAllAliasesSuccess() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Alias> page = new PageImpl<>(Arrays.asList(testAlias), pageable, 1);

        when(aliasRepository.findAll(pageable)).thenReturn(page);

        // Act & Assert
        StepVerifier.create(domainService.getAllAliases(pageable))
                .expectNext(page)
                .verifyComplete();

        verify(aliasRepository).findAll(pageable);
    }

    @Test
    @DisplayName("Should get alias by ID successfully")
    void testGetAliasByIdSuccess() {
        // Arrange
        when(aliasRepository.findById(1L)).thenReturn(Optional.of(testAlias));

        // Act & Assert
        StepVerifier.create(domainService.getAliasById(1L))
                .expectNext(testAlias)
                .verifyComplete();

        verify(aliasRepository).findById(1L);
    }

    @Test
    @DisplayName("Should return error when alias not found by ID")
    void testGetAliasByIdNotFound() {
        // Arrange
        when(aliasRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(domainService.getAliasById(999L))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Alias not found: 999"))
                .verify();

        verify(aliasRepository).findById(999L);
    }

    @Test
    @DisplayName("Should create alias successfully")
    void testCreateAliasSuccess() {
        // Arrange
        Alias newAlias = Alias.builder()
                .id(2L)
                .source("newalias@example.com")
                .destination("user@example.com")
                .build();

        when(domainRepository.existsByDomain("example.com")).thenReturn(true);
        when(aliasRepository.findBySource("newalias@example.com")).thenReturn(Collections.emptyList());
        when(aliasRepository.save(any(Alias.class))).thenReturn(newAlias);

        // Act & Assert
        StepVerifier.create(domainService.createAlias("newalias@example.com", "user@example.com"))
                .expectNext(newAlias)
                .verifyComplete();

        verify(domainRepository).existsByDomain("example.com");
        verify(aliasRepository).save(any(Alias.class));
    }

    @Test
    @DisplayName("Should reject creating alias with invalid email format")
    void testCreateAliasInvalidFormat() {
        // Act & Assert - Invalid source
        StepVerifier.create(domainService.createAlias("invalidemail", "user@example.com"))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().contains("Invalid email format"))
                .verify();

        verify(aliasRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject creating alias for non-existent domain")
    void testCreateAliasDomainNotFound() {
        // Arrange
        when(domainRepository.existsByDomain("nonexistent.com")).thenReturn(false);

        // Act & Assert
        StepVerifier.create(domainService.createAlias("alias@nonexistent.com", "user@example.com"))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().contains("Source domain does not exist"))
                .verify();

        verify(aliasRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject creating duplicate alias")
    void testCreateAliasDuplicate() {
        // Arrange
        when(domainRepository.existsByDomain("example.com")).thenReturn(true);
        when(aliasRepository.findBySource("alias@example.com")).thenReturn(Arrays.asList(testAlias));

        // Act & Assert
        StepVerifier.create(domainService.createAlias("alias@example.com", "user@example.com"))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().contains("Alias already exists"))
                .verify();

        verify(aliasRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update alias successfully")
    void testUpdateAliasSuccess() {
        // Arrange
        Alias updatedAlias = Alias.builder()
                .id(1L)
                .source("alias@example.com")
                .destination("newuser@example.com")
                .build();

        when(aliasRepository.findById(1L)).thenReturn(Optional.of(testAlias));
        when(aliasRepository.save(any(Alias.class))).thenReturn(updatedAlias);

        // Act & Assert
        StepVerifier.create(domainService.updateAlias(1L, "newuser@example.com"))
                .expectNext(updatedAlias)
                .verifyComplete();

        verify(aliasRepository).findById(1L);
        verify(aliasRepository).save(any(Alias.class));
    }

    @Test
    @DisplayName("Should reject updating alias with invalid destination")
    void testUpdateAliasInvalidDestination() {
        // Arrange
        when(aliasRepository.findById(1L)).thenReturn(Optional.of(testAlias));

        // Act & Assert
        StepVerifier.create(domainService.updateAlias(1L, "invalidemail"))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().contains("Invalid email format"))
                .verify();

        verify(aliasRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject updating non-existent alias")
    void testUpdateAliasNotFound() {
        // Arrange
        when(aliasRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(domainService.updateAlias(999L, "user@example.com"))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Alias not found: 999"))
                .verify();

        verify(aliasRepository).findById(999L);
        verify(aliasRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete alias successfully")
    void testDeleteAliasSuccess() {
        // Arrange
        when(aliasRepository.existsById(1L)).thenReturn(true);
        doNothing().when(aliasRepository).deleteById(1L);

        // Act & Assert
        StepVerifier.create(domainService.deleteAlias(1L))
                .verifyComplete();

        verify(aliasRepository).existsById(1L);
        verify(aliasRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Should reject deleting non-existent alias")
    void testDeleteAliasNotFound() {
        // Arrange
        when(aliasRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        StepVerifier.create(domainService.deleteAlias(999L))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Alias not found: 999"))
                .verify();

        verify(aliasRepository).existsById(999L);
        verify(aliasRepository, never()).deleteById(any());
    }
}
