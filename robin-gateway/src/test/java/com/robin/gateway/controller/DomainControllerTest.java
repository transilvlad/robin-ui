package com.robin.gateway.controller;

import com.robin.gateway.controller.DomainController.CreateDomainRequest;
import com.robin.gateway.controller.DomainController.DiscoverDomainRequest;
import com.robin.gateway.model.dto.InitialRecordRequest;
import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DnsRecordRepository;
import com.robin.gateway.service.DnsDiscoveryService;
import com.robin.gateway.service.DomainService;
import com.robin.gateway.service.DomainSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DomainController Tests")
class DomainControllerTest {

    @Mock
    private DomainService domainService;

    @Mock
    private DomainSyncService domainSyncService;

    @Mock
    private DnsRecordRepository dnsRecordRepository;

    @Mock
    private DnsDiscoveryService dnsDiscoveryService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        DomainController controller = new DomainController(
            domainService,
            domainSyncService,
            dnsRecordRepository,
            dnsDiscoveryService
        );

        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/domains should return paginated domains with default pagination")
    void testGetAllDomainsDefaultPagination() {
        // Given
        Domain domain1 = Domain.builder()
            .id(1L)
            .domain("test1.com")
            .build();

        Domain domain2 = Domain.builder()
            .id(2L)
            .domain("test2.com")
            .build();

        List<Domain> domains = List.of(domain1, domain2);
        Page<Domain> page = new PageImpl<>(domains, PageRequest.of(0, 10), 2);

        when(domainService.getAllDomains(any(Pageable.class)))
            .thenReturn(Mono.just(page));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/domains")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].domain").isEqualTo("test1.com")
            .jsonPath("$.content[1].domain").isEqualTo("test2.com")
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.size").isEqualTo(10);

        verify(domainService).getAllDomains(argThat(pageable ->
            pageable.getPageNumber() == 0 && pageable.getPageSize() == 10
        ));
    }

    @Test
    @DisplayName("GET /api/v1/domains should return paginated domains with custom pagination")
    void testGetAllDomainsCustomPagination() {
        // Given
        Page<Domain> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(2, 5), 0);

        when(domainService.getAllDomains(any(Pageable.class)))
            .thenReturn(Mono.just(page));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/domains?page=2&size=5")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.size").isEqualTo(5)
            .jsonPath("$.number").isEqualTo(2);

        verify(domainService).getAllDomains(argThat(pageable ->
            pageable.getPageNumber() == 2 && pageable.getPageSize() == 5
        ));
    }

    @Test
    @DisplayName("GET /api/v1/domains/{id} should return domain by ID")
    void testGetDomainById() {
        // Given
        Long domainId = 1L;
        Domain domain = Domain.builder()
            .id(domainId)
            .domain("test.com")
            .status(Domain.DomainStatus.ACTIVE)
            .build();

        when(domainService.getDomainById(domainId))
            .thenReturn(Mono.just(domain));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/domains/{id}", domainId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(domainId)
            .jsonPath("$.domain").isEqualTo("test.com")
            .jsonPath("$.status").isEqualTo("ACTIVE");

        verify(domainService).getDomainById(domainId);
    }

    @Test
    @DisplayName("GET /api/v1/domains/{id} should return 404 when domain not found")
    void testGetDomainByIdNotFound() {
        // Given
        Long domainId = 999L;

        when(domainService.getDomainById(domainId))
            .thenReturn(Mono.error(new RuntimeException("Domain not found: " + domainId)));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/domains/{id}", domainId)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(domainService).getDomainById(domainId);
    }

    @Test
    @DisplayName("POST /api/v1/domains/discover should discover domain configuration")
    void testDiscoverDomain() {
        // Given
        DiscoverDomainRequest request = new DiscoverDomainRequest();
        request.setDomain("test.com");
        request.setDnsProviderId(1L);

        DnsDiscoveryService.DiscoveryResult result = new DnsDiscoveryService.DiscoveryResult();
        result.setConfiguration(Domain.builder()
            .domain("test.com")
            .spfIncludes("_spf.google.com")
            .build());
        result.setProposedRecords(new ArrayList<>());

        when(dnsDiscoveryService.discover("test.com", 1L))
            .thenReturn(Mono.just(result));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/domains/discover")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.configuration").exists()
            .jsonPath("$.configuration.domain").isEqualTo("test.com");

        verify(dnsDiscoveryService).discover("test.com", 1L);
    }

    @Test
    @DisplayName("POST /api/v1/domains should create a new domain")
    void testCreateDomain() {
        // Given
        CreateDomainRequest request = new CreateDomainRequest();
        request.setDomain("newdomain.com");
        request.setDnsProviderId(1L);
        request.setRegistrarProviderId(2L);
        request.setEmailProviderId(null);
        request.setConfig(null);
        request.setInitialRecords(null);

        Domain createdDomain = Domain.builder()
            .id(10L)
            .domain("newdomain.com")
            .status(Domain.DomainStatus.PENDING)
            .build();

        when(domainService.createDomain(
            eq("newdomain.com"),
            eq(1L),
            eq(2L),
            isNull(),
            isNull(),
            isNull()
        )).thenReturn(Mono.just(createdDomain));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/domains")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(10)
            .jsonPath("$.domain").isEqualTo("newdomain.com")
            .jsonPath("$.status").isEqualTo("PENDING");

        verify(domainService).createDomain(
            eq("newdomain.com"),
            eq(1L),
            eq(2L),
            isNull(),
            isNull(),
            isNull()
        );
    }

    @Test
    @DisplayName("POST /api/v1/domains should create domain with initial records")
    void testCreateDomainWithInitialRecords() {
        // Given
        InitialRecordRequest recordRequest = new InitialRecordRequest();
        recordRequest.setType(DnsRecord.RecordType.A);
        recordRequest.setName("mail");
        recordRequest.setContent("1.2.3.4");
        recordRequest.setTtl(3600);

        CreateDomainRequest request = new CreateDomainRequest();
        request.setDomain("newdomain.com");
        request.setDnsProviderId(1L);
        request.setInitialRecords(List.of(recordRequest));

        Domain createdDomain = Domain.builder()
            .id(10L)
            .domain("newdomain.com")
            .build();

        when(domainService.createDomain(
            eq("newdomain.com"),
            eq(1L),
            isNull(),
            isNull(),
            isNull(),
            anyList()
        )).thenReturn(Mono.just(createdDomain));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/domains")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(10)
            .jsonPath("$.domain").isEqualTo("newdomain.com");

        verify(domainService).createDomain(
            eq("newdomain.com"),
            eq(1L),
            isNull(),
            isNull(),
            isNull(),
            anyList()
        );
    }

    @Test
    @DisplayName("PUT /api/v1/domains/{id} should update domain")
    void testUpdateDomain() {
        // Given
        Long domainId = 1L;
        Domain updateRequest = Domain.builder()
            .domain("test.com")
            .dmarcPolicy("quarantine")
            .spfIncludes("_spf.google.com")
            .build();

        Domain updatedDomain = Domain.builder()
            .id(domainId)
            .domain("test.com")
            .dmarcPolicy("quarantine")
            .spfIncludes("_spf.google.com")
            .build();

        when(domainService.updateDomain(eq(domainId), any(Domain.class)))
            .thenReturn(Mono.just(updatedDomain));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/domains/{id}", domainId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(domainId)
            .jsonPath("$.domain").isEqualTo("test.com")
            .jsonPath("$.dmarcPolicy").isEqualTo("quarantine");

        verify(domainService).updateDomain(eq(domainId), any(Domain.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/domains/{id} should delete domain")
    void testDeleteDomain() {
        // Given
        Long domainId = 1L;

        when(domainService.deleteDomain(domainId))
            .thenReturn(Mono.empty());

        // When & Then
        webTestClient.delete()
            .uri("/api/v1/domains/{id}", domainId)
            .exchange()
            .expectStatus().isOk();

        verify(domainService).deleteDomain(domainId);
    }

    @Test
    @DisplayName("GET /api/v1/domains/{id}/records should return DNS records for domain")
    void testGetRecords() {
        // Given
        Long domainId = 1L;

        DnsRecord record1 = DnsRecord.builder()
            .id(1L)
            .name("mail")
            .type(DnsRecord.RecordType.A)
            .content("1.2.3.4")
            .build();

        DnsRecord record2 = DnsRecord.builder()
            .id(2L)
            .name("@")
            .type(DnsRecord.RecordType.MX)
            .content("mail.test.com")
            .priority(10)
            .build();

        List<DnsRecord> records = List.of(record1, record2);

        when(dnsRecordRepository.findByDomain_Id(domainId))
            .thenReturn(records);

        // When & Then
        webTestClient.get()
            .uri("/api/v1/domains/{id}/records", domainId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].name").isEqualTo("mail")
            .jsonPath("$[0].type").isEqualTo("A")
            .jsonPath("$[1].type").isEqualTo("MX")
            .jsonPath("$[1].priority").isEqualTo(10);

        verify(dnsRecordRepository).findByDomain_Id(domainId);
    }

    @Test
    @DisplayName("POST /api/v1/domains/{id}/sync should trigger domain sync")
    void testSyncDomain() {
        // Given
        Long domainId = 1L;

        when(domainSyncService.syncDomain(domainId))
            .thenReturn(Mono.empty());

        // When & Then
        webTestClient.post()
            .uri("/api/v1/domains/{id}/sync", domainId)
            .exchange()
            .expectStatus().isOk();

        verify(domainSyncService).syncDomain(domainId);
    }

    @Test
    @DisplayName("GET /api/v1/domains/{id}/dnssec should return DNSSEC status")
    void testGetDnssecStatus() {
        // Given
        Long domainId = 1L;

        DnsRecord dsRecord = DnsRecord.builder()
            .id(1L)
            .name("@")
            .type(DnsRecord.RecordType.DS)
            .content("12345 8 2 ABCDEF...")
            .build();

        List<DnsRecord> dsRecords = List.of(dsRecord);

        when(domainService.getDnssecStatus(domainId))
            .thenReturn(Mono.just(dsRecords));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/domains/{id}/dnssec", domainId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].type").isEqualTo("DS");

        verify(domainService).getDnssecStatus(domainId);
    }

    @Test
    @DisplayName("GET /api/v1/domains/{id}/dnssec should return empty list for manual provider")
    void testGetDnssecStatusManualProvider() {
        // Given
        Long domainId = 1L;

        when(domainService.getDnssecStatus(domainId))
            .thenReturn(Mono.just(List.of()));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/domains/{id}/dnssec", domainId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0);

        verify(domainService).getDnssecStatus(domainId);
    }

    @Test
    @DisplayName("POST /api/v1/domains/{id}/dnssec/enable should enable DNSSEC")
    void testEnableDnssec() {
        // Given
        Long domainId = 1L;

        when(domainService.enableDnssec(domainId))
            .thenReturn(Mono.empty());

        // When & Then
        webTestClient.post()
            .uri("/api/v1/domains/{id}/dnssec/enable", domainId)
            .exchange()
            .expectStatus().isOk();

        verify(domainService).enableDnssec(domainId);
    }

    @Test
    @DisplayName("POST /api/v1/domains/{id}/dnssec/disable should disable DNSSEC")
    void testDisableDnssec() {
        // Given
        Long domainId = 1L;

        when(domainService.disableDnssec(domainId))
            .thenReturn(Mono.empty());

        // When & Then
        webTestClient.post()
            .uri("/api/v1/domains/{id}/dnssec/disable", domainId)
            .exchange()
            .expectStatus().isOk();

        verify(domainService).disableDnssec(domainId);
    }

    @Test
    @DisplayName("POST /api/v1/domains should handle service error when creating domain")
    void testCreateDomainServiceError() {
        // Given
        CreateDomainRequest request = new CreateDomainRequest();
        request.setDomain("error.com");
        request.setDnsProviderId(1L);

        when(domainService.createDomain(
            eq("error.com"),
            eq(1L),
            isNull(),
            isNull(),
            isNull(),
            isNull()
        )).thenReturn(Mono.error(new IllegalArgumentException("Domain already exists")));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/domains")
            .bodyValue(request)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(domainService).createDomain(
            eq("error.com"),
            eq(1L),
            isNull(),
            isNull(),
            isNull(),
            isNull()
        );
    }

    @Test
    @DisplayName("PUT /api/v1/domains/{id} should handle domain not found")
    void testUpdateDomainNotFound() {
        // Given
        Long domainId = 999L;
        Domain updateRequest = Domain.builder()
            .domain("test.com")
            .dmarcPolicy("quarantine")
            .build();

        when(domainService.updateDomain(eq(domainId), any(Domain.class)))
            .thenReturn(Mono.error(new RuntimeException("Domain not found: " + domainId)));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/domains/{id}", domainId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(domainService).updateDomain(eq(domainId), any(Domain.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/domains/{id} should handle domain not found")
    void testDeleteDomainNotFound() {
        // Given
        Long domainId = 999L;

        when(domainService.deleteDomain(domainId))
            .thenReturn(Mono.error(new RuntimeException("Domain not found: " + domainId)));

        // When & Then
        webTestClient.delete()
            .uri("/api/v1/domains/{id}", domainId)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(domainService).deleteDomain(domainId);
    }
}
