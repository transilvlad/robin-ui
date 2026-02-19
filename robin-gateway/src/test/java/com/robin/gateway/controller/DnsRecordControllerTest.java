package com.robin.gateway.controller;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.service.DnsRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DnsRecordController Tests")
class DnsRecordControllerTest {

    @Mock
    private DnsRecordService dnsRecordService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        DnsRecordController controller = new DnsRecordController(dnsRecordService);

        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("PUT /api/v1/dns-records/{id} should update A record")
    void testUpdateARecord() {
        // Given
        Long recordId = 1L;

        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .build();

        DnsRecord updateRequest = DnsRecord.builder()
            .type(DnsRecord.RecordType.A)
            .name("mail")
            .content("1.2.3.5") // Changed IP
            .ttl(3600)
            .build();

        DnsRecord updatedRecord = DnsRecord.builder()
            .id(recordId)
            .domain(domain)
            .type(DnsRecord.RecordType.A)
            .name("mail")
            .content("1.2.3.5")
            .ttl(3600)
            .syncStatus(DnsRecord.SyncStatus.PENDING)
            .build();

        when(dnsRecordService.updateRecord(eq(recordId), any(DnsRecord.class)))
            .thenReturn(Mono.just(updatedRecord));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/dns-records/{id}", recordId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(recordId)
            .jsonPath("$.type").isEqualTo("A")
            .jsonPath("$.name").isEqualTo("mail")
            .jsonPath("$.content").isEqualTo("1.2.3.5")
            .jsonPath("$.ttl").isEqualTo(3600)
            .jsonPath("$.syncStatus").isEqualTo("PENDING");

        verify(dnsRecordService).updateRecord(eq(recordId), any(DnsRecord.class));
    }

    @Test
    @DisplayName("PUT /api/v1/dns-records/{id} should update MX record with priority")
    void testUpdateMxRecord() {
        // Given
        Long recordId = 2L;

        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .build();

        DnsRecord updateRequest = DnsRecord.builder()
            .type(DnsRecord.RecordType.MX)
            .name("@")
            .content("mail.test.com.")
            .priority(5) // Changed from 10 to 5
            .ttl(3600)
            .build();

        DnsRecord updatedRecord = DnsRecord.builder()
            .id(recordId)
            .domain(domain)
            .type(DnsRecord.RecordType.MX)
            .name("@")
            .content("mail.test.com.")
            .priority(5)
            .ttl(3600)
            .syncStatus(DnsRecord.SyncStatus.PENDING)
            .build();

        when(dnsRecordService.updateRecord(eq(recordId), any(DnsRecord.class)))
            .thenReturn(Mono.just(updatedRecord));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/dns-records/{id}", recordId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(recordId)
            .jsonPath("$.type").isEqualTo("MX")
            .jsonPath("$.priority").isEqualTo(5)
            .jsonPath("$.syncStatus").isEqualTo("PENDING");

        verify(dnsRecordService).updateRecord(eq(recordId), any(DnsRecord.class));
    }

    @Test
    @DisplayName("PUT /api/v1/dns-records/{id} should update TXT record for SPF")
    void testUpdateSpfRecord() {
        // Given
        Long recordId = 3L;

        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .build();

        DnsRecord updateRequest = DnsRecord.builder()
            .type(DnsRecord.RecordType.TXT)
            .name("@")
            .content("v=spf1 include:_spf.google.com include:_spf.sendgrid.net ~all")
            .purpose(DnsRecord.RecordPurpose.SPF)
            .ttl(3600)
            .build();

        DnsRecord updatedRecord = DnsRecord.builder()
            .id(recordId)
            .domain(domain)
            .type(DnsRecord.RecordType.TXT)
            .name("@")
            .content("v=spf1 include:_spf.google.com include:_spf.sendgrid.net ~all")
            .purpose(DnsRecord.RecordPurpose.SPF)
            .ttl(3600)
            .syncStatus(DnsRecord.SyncStatus.PENDING)
            .build();

        when(dnsRecordService.updateRecord(eq(recordId), any(DnsRecord.class)))
            .thenReturn(Mono.just(updatedRecord));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/dns-records/{id}", recordId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(recordId)
            .jsonPath("$.type").isEqualTo("TXT")
            .jsonPath("$.purpose").isEqualTo("SPF")
            .jsonPath("$.content").value(org.hamcrest.Matchers.containsString("_spf.google.com"))
            .jsonPath("$.syncStatus").isEqualTo("PENDING");

        verify(dnsRecordService).updateRecord(eq(recordId), any(DnsRecord.class));
    }

    @Test
    @DisplayName("PUT /api/v1/dns-records/{id} should update DMARC record")
    void testUpdateDmarcRecord() {
        // Given
        Long recordId = 4L;

        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .build();

        DnsRecord updateRequest = DnsRecord.builder()
            .type(DnsRecord.RecordType.TXT)
            .name("_dmarc")
            .content("v=DMARC1; p=quarantine; rua=mailto:dmarc@test.com")
            .purpose(DnsRecord.RecordPurpose.DMARC)
            .ttl(3600)
            .build();

        DnsRecord updatedRecord = DnsRecord.builder()
            .id(recordId)
            .domain(domain)
            .type(DnsRecord.RecordType.TXT)
            .name("_dmarc")
            .content("v=DMARC1; p=quarantine; rua=mailto:dmarc@test.com")
            .purpose(DnsRecord.RecordPurpose.DMARC)
            .ttl(3600)
            .syncStatus(DnsRecord.SyncStatus.PENDING)
            .build();

        when(dnsRecordService.updateRecord(eq(recordId), any(DnsRecord.class)))
            .thenReturn(Mono.just(updatedRecord));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/dns-records/{id}", recordId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(recordId)
            .jsonPath("$.type").isEqualTo("TXT")
            .jsonPath("$.name").isEqualTo("_dmarc")
            .jsonPath("$.purpose").isEqualTo("DMARC")
            .jsonPath("$.content").value(org.hamcrest.Matchers.containsString("p=quarantine"));

        verify(dnsRecordService).updateRecord(eq(recordId), any(DnsRecord.class));
    }

    @Test
    @DisplayName("PUT /api/v1/dns-records/{id} should update CNAME record")
    void testUpdateCnameRecord() {
        // Given
        Long recordId = 5L;

        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .build();

        DnsRecord updateRequest = DnsRecord.builder()
            .type(DnsRecord.RecordType.CNAME)
            .name("www")
            .content("test.com.")
            .ttl(3600)
            .build();

        DnsRecord updatedRecord = DnsRecord.builder()
            .id(recordId)
            .domain(domain)
            .type(DnsRecord.RecordType.CNAME)
            .name("www")
            .content("test.com.")
            .ttl(3600)
            .syncStatus(DnsRecord.SyncStatus.PENDING)
            .build();

        when(dnsRecordService.updateRecord(eq(recordId), any(DnsRecord.class)))
            .thenReturn(Mono.just(updatedRecord));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/dns-records/{id}", recordId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(recordId)
            .jsonPath("$.type").isEqualTo("CNAME")
            .jsonPath("$.name").isEqualTo("www")
            .jsonPath("$.content").isEqualTo("test.com.");

        verify(dnsRecordService).updateRecord(eq(recordId), any(DnsRecord.class));
    }

    @Test
    @DisplayName("PUT /api/v1/dns-records/{id} should handle record not found")
    void testUpdateRecordNotFound() {
        // Given
        Long recordId = 999L;

        DnsRecord updateRequest = DnsRecord.builder()
            .type(DnsRecord.RecordType.A)
            .name("mail")
            .content("1.2.3.4")
            .ttl(3600)
            .build();

        when(dnsRecordService.updateRecord(eq(recordId), any(DnsRecord.class)))
            .thenReturn(Mono.error(new RuntimeException("DNS Record not found: " + recordId)));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/dns-records/{id}", recordId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(dnsRecordService).updateRecord(eq(recordId), any(DnsRecord.class));
    }

    @Test
    @DisplayName("PUT /api/v1/dns-records/{id} should mark record as PENDING after update")
    void testUpdateRecordMarksPending() {
        // Given
        Long recordId = 1L;

        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .build();

        DnsRecord updateRequest = DnsRecord.builder()
            .type(DnsRecord.RecordType.A)
            .name("mail")
            .content("1.2.3.5")
            .ttl(3600)
            .build();

        DnsRecord updatedRecord = DnsRecord.builder()
            .id(recordId)
            .domain(domain)
            .type(DnsRecord.RecordType.A)
            .name("mail")
            .content("1.2.3.5")
            .ttl(3600)
            .syncStatus(DnsRecord.SyncStatus.PENDING) // Marked for re-sync
            .build();

        when(dnsRecordService.updateRecord(eq(recordId), any(DnsRecord.class)))
            .thenReturn(Mono.just(updatedRecord));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/dns-records/{id}", recordId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.syncStatus").isEqualTo("PENDING");

        verify(dnsRecordService).updateRecord(eq(recordId), any(DnsRecord.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/dns-records/{id} should delete record successfully")
    void testDeleteRecord() {
        // Given
        Long recordId = 1L;

        when(dnsRecordService.deleteRecord(recordId))
            .thenReturn(Mono.empty());

        // When & Then
        webTestClient.delete()
            .uri("/api/v1/dns-records/{id}", recordId)
            .exchange()
            .expectStatus().isOk();

        verify(dnsRecordService).deleteRecord(recordId);
    }

    @Test
    @DisplayName("DELETE /api/v1/dns-records/{id} should handle service error")
    void testDeleteRecordServiceError() {
        // Given
        Long recordId = 1L;

        when(dnsRecordService.deleteRecord(recordId))
            .thenReturn(Mono.error(new RuntimeException("Database error")));

        // When & Then
        webTestClient.delete()
            .uri("/api/v1/dns-records/{id}", recordId)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(dnsRecordService).deleteRecord(recordId);
    }

    @Test
    @DisplayName("PUT /api/v1/dns-records/{id} should update record TTL")
    void testUpdateRecordTtl() {
        // Given
        Long recordId = 1L;

        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .build();

        DnsRecord updateRequest = DnsRecord.builder()
            .type(DnsRecord.RecordType.A)
            .name("mail")
            .content("1.2.3.4")
            .ttl(7200) // Changed from 3600 to 7200
            .build();

        DnsRecord updatedRecord = DnsRecord.builder()
            .id(recordId)
            .domain(domain)
            .type(DnsRecord.RecordType.A)
            .name("mail")
            .content("1.2.3.4")
            .ttl(7200)
            .syncStatus(DnsRecord.SyncStatus.PENDING)
            .build();

        when(dnsRecordService.updateRecord(eq(recordId), any(DnsRecord.class)))
            .thenReturn(Mono.just(updatedRecord));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/dns-records/{id}", recordId)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.ttl").isEqualTo(7200)
            .jsonPath("$.syncStatus").isEqualTo("PENDING");

        verify(dnsRecordService).updateRecord(eq(recordId), any(DnsRecord.class));
    }
}
