package com.robin.gateway.service;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.ProviderConfig;
import com.robin.gateway.repository.DomainRepository;
import com.robin.gateway.repository.ProviderConfigRepository;
import com.robin.gateway.service.dns.DnsProvider;
import com.robin.gateway.service.dns.DnsProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("DnsDiscoveryService Tests")
class DnsDiscoveryServiceTest {

    @Mock
    private DnsRecordGenerator dnsRecordGenerator;

    @Mock
    private ProviderConfigRepository providerRepository;

    @Mock
    private DnsProviderFactory dnsProviderFactory;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private DnsProvider dnsProvider;

    private DnsDiscoveryService dnsDiscoveryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dnsDiscoveryService = new DnsDiscoveryService(
                dnsRecordGenerator,
                providerRepository,
                dnsProviderFactory,
                domainRepository
        );
    }

    @Test
    @DisplayName("Should throw exception when domain already exists")
    void testDiscoverDomainAlreadyExists() {
        String domainName = "existing.com";
        when(domainRepository.existsByDomain(domainName)).thenReturn(true);

        StepVerifier.create(dnsDiscoveryService.discover(domainName, null))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                                throwable.getMessage().contains("Domain already exists"))
                .verify();
    }

    @Test
    @DisplayName("Should perform API discovery with valid provider")
    void testDiscoverWithApiProvider() {
        String domainName = "test.com";
        Long providerId = 1L;

        // Setup domain repository
        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        // Setup provider
        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setId(providerId);
        providerConfig.setName("Cloudflare");
        providerConfig.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerConfig));

        // Setup provider factory
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);

        // Setup DNS records from API
        List<DnsRecord> apiRecords = List.of(
                DnsRecord.builder()
                        .name("@")
                        .type(DnsRecord.RecordType.A)
                        .content("1.2.3.4")
                        .build(),
                DnsRecord.builder()
                        .name("mail." + domainName)
                        .type(DnsRecord.RecordType.A)
                        .content("1.2.3.5")
                        .build()
        );
        when(dnsProvider.listRecords(any(Domain.class))).thenReturn(apiRecords);

        // Setup proposed records
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        // Execute
        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getDiscoveredRecords()).isNotEmpty();
                    assertThat(result.getConfiguration()).isNotNull();
                    assertThat(result.getConfiguration().getDomain()).isEqualTo(domainName);
                })
                .verifyComplete();

        verify(dnsProvider).listRecords(any(Domain.class));
        verify(dnsRecordGenerator).generateExpectedRecords(any(Domain.class));
    }

    @Test
    @DisplayName("Should handle provider not found")
    void testDiscoverProviderNotFound() {
        String domainName = "test.com";
        Long providerId = 999L;

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);
        when(providerRepository.findById(providerId)).thenReturn(Optional.empty());
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    // Should still complete with empty/fallback results
                    assertThat(result.getConfiguration().getDomain()).isEqualTo(domainName);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should discover domain without provider (public lookup)")
    void testDiscoverWithoutProvider() {
        String domainName = "test.com";

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        StepVerifier.create(dnsDiscoveryService.discover(domainName))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getConfiguration()).isNotNull();
                    assertThat(result.getConfiguration().getDomain()).isEqualTo(domainName);
                    // Public lookup may or may not find records depending on actual DNS
                })
                .verifyComplete();

        verify(dnsRecordGenerator).generateExpectedRecords(any(Domain.class));
    }

    @Test
    @DisplayName("Should parse SPF from TXT records in API discovery")
    void testDiscoverParseSpfFromApi() {
        String domainName = "test.com";
        Long providerId = 1L;

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setId(providerId);
        providerConfig.setName("Test Provider");
        providerConfig.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerConfig));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);

        List<DnsRecord> apiRecords = List.of(
                DnsRecord.builder()
                        .name("@")
                        .type(DnsRecord.RecordType.TXT)
                        .content("\"v=spf1 include:_spf.google.com ~all\"")
                        .build()
        );
        when(dnsProvider.listRecords(any(Domain.class))).thenReturn(apiRecords);
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    assertThat(result.getConfiguration().getSpfSoftFail()).isTrue();
                    assertThat(result.getConfiguration().getSpfIncludes()).contains("_spf.google.com");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should parse DMARC from TXT records in API discovery")
    void testDiscoverParseDmarcFromApi() {
        String domainName = "test.com";
        Long providerId = 1L;

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setId(providerId);
        providerConfig.setName("Test Provider");
        providerConfig.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerConfig));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);

        List<DnsRecord> apiRecords = List.of(
                DnsRecord.builder()
                        .name("@")
                        .type(DnsRecord.RecordType.TXT)
                        .content("\"v=DMARC1; p=quarantine; sp=reject; pct=100; adkim=r; rua=mailto:dmarc@test.com\"")
                        .build()
        );
        when(dnsProvider.listRecords(any(Domain.class))).thenReturn(apiRecords);
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    Domain config = result.getConfiguration();
                    assertThat(config.getDmarcPolicy()).isEqualTo("quarantine");
                    assertThat(config.getDmarcSubdomainPolicy()).isEqualTo("reject");
                    assertThat(config.getDmarcPercentage()).isEqualTo(100);
                    assertThat(config.getDmarcAlignment()).isEqualTo("r");
                    assertThat(config.getDmarcReportingEmail()).isEqualTo("dmarc@test.com");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle API discovery failure gracefully")
    void testDiscoverApiFailure() {
        String domainName = "test.com";
        Long providerId = 1L;

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setId(providerId);
        providerConfig.setName("Test Provider");
        providerConfig.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerConfig));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(any(Domain.class))).thenThrow(new RuntimeException("API error"));
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        // Should not fail completely, should fall back to public lookup
        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getConfiguration().getDomain()).isEqualTo(domainName);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should filter API records for specific domain")
    void testDiscoverFilterApiRecords() {
        String domainName = "test.com";
        Long providerId = 1L;

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setId(providerId);
        providerConfig.setName("Test Provider");
        providerConfig.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerConfig));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);

        // Mix of records for different domains (Cloudflare returns all domains in account)
        List<DnsRecord> apiRecords = List.of(
                DnsRecord.builder().name("@").type(DnsRecord.RecordType.A).content("1.2.3.4").build(),
                DnsRecord.builder().name("test.com").type(DnsRecord.RecordType.A).content("1.2.3.4").build(),
                DnsRecord.builder().name("other.com").type(DnsRecord.RecordType.A).content("1.2.3.5").build(), // Should be filtered
                DnsRecord.builder().name("mail.test.com").type(DnsRecord.RecordType.A).content("1.2.3.6").build()
        );
        when(dnsProvider.listRecords(any(Domain.class))).thenReturn(apiRecords);
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    List<DnsRecord> discovered = result.getDiscoveredRecords();
                    // Should only include records for test.com (@ and records containing test.com)
                    assertThat(discovered).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(discovered).noneMatch(r -> r.getName().equals("other.com"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should set provider type on domain configuration")
    void testDiscoverSetsProviderType() {
        String domainName = "test.com";
        Long providerId = 1L;

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setId(providerId);
        providerConfig.setName("Cloudflare");
        providerConfig.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerConfig));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);
        when(dnsProvider.listRecords(any(Domain.class))).thenReturn(new ArrayList<>());
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    Domain config = result.getConfiguration();
                    assertThat(config.getDnsProviderType()).isEqualTo(Domain.DnsProviderType.CLOUDFLARE);
                    assertThat(config.getDnsProvider()).isEqualTo(providerConfig);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should generate proposed records from discovered configuration")
    void testDiscoverGeneratesProposedRecords() {
        String domainName = "test.com";

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        List<DnsRecord> proposedRecords = List.of(
                DnsRecord.builder()
                        .name("@")
                        .type(DnsRecord.RecordType.MX)
                        .content("mail.test.com")
                        .priority(10)
                        .build()
        );
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(proposedRecords);

        StepVerifier.create(dnsDiscoveryService.discover(domainName))
                .assertNext(result -> {
                    assertThat(result.getProposedRecords()).isNotEmpty();
                    assertThat(result.getProposedRecords()).hasSize(1);
                    assertThat(result.getProposedRecords().get(0).getType()).isEqualTo(DnsRecord.RecordType.MX);
                })
                .verifyComplete();

        verify(dnsRecordGenerator).generateExpectedRecords(any(Domain.class));
    }

    @Test
    @DisplayName("Should handle empty API results and fallback to public lookup")
    void testDiscoverEmptyApiResultsFallback() {
        String domainName = "test.com";
        Long providerId = 1L;

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setId(providerId);
        providerConfig.setName("Test Provider");
        providerConfig.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerConfig));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);

        // API returns empty list (no records found)
        when(dnsProvider.listRecords(any(Domain.class))).thenReturn(new ArrayList<>());
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    // Should have attempted public lookup as fallback
                    assertThat(result.getConfiguration().getDomain()).isEqualTo(domainName);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should parse SPF with multiple includes")
    void testDiscoverParseSpfMultipleIncludes() {
        String domainName = "test.com";
        Long providerId = 1L;

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setId(providerId);
        providerConfig.setName("Test Provider");
        providerConfig.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerConfig));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);

        List<DnsRecord> apiRecords = List.of(
                DnsRecord.builder()
                        .name("@")
                        .type(DnsRecord.RecordType.TXT)
                        .content("v=spf1 include:_spf.google.com include:_spf.sendgrid.net include:spf.protection.outlook.com ~all")
                        .build()
        );
        when(dnsProvider.listRecords(any(Domain.class))).thenReturn(apiRecords);
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    String spfIncludes = result.getConfiguration().getSpfIncludes();
                    assertThat(spfIncludes).contains("_spf.google.com");
                    assertThat(spfIncludes).contains("_spf.sendgrid.net");
                    assertThat(spfIncludes).contains("spf.protection.outlook.com");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle DMARC with minimal tags")
    void testDiscoverParseDmarcMinimal() {
        String domainName = "test.com";
        Long providerId = 1L;

        when(domainRepository.existsByDomain(domainName)).thenReturn(false);

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setId(providerId);
        providerConfig.setName("Test Provider");
        providerConfig.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerConfig));
        when(dnsProviderFactory.getProvider(Domain.DnsProviderType.CLOUDFLARE)).thenReturn(dnsProvider);

        List<DnsRecord> apiRecords = List.of(
                DnsRecord.builder()
                        .name("@")
                        .type(DnsRecord.RecordType.TXT)
                        .content("v=DMARC1; p=none")
                        .build()
        );
        when(dnsProvider.listRecords(any(Domain.class))).thenReturn(apiRecords);
        when(dnsRecordGenerator.generateExpectedRecords(any(Domain.class))).thenReturn(new ArrayList<>());

        StepVerifier.create(dnsDiscoveryService.discover(domainName, providerId))
                .assertNext(result -> {
                    Domain config = result.getConfiguration();
                    assertThat(config.getDmarcPolicy()).isEqualTo("none");
                    // Other fields should be null/default
                })
                .verifyComplete();
    }
}
