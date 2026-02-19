package com.robin.gateway.service;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DnsRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for DnsRecordService.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>DNS record update operations</li>
 *   <li>DNS record deletion</li>
 *   <li>Error handling</li>
 *   <li>Reactive operations</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DnsRecordServiceTest {

    @Mock
    private DnsRecordRepository dnsRecordRepository;

    @InjectMocks
    private DnsRecordService dnsRecordService;

    private DnsRecord testRecord;
    private Domain testDomain;

    @BeforeEach
    void setUp() {
        testDomain = Domain.builder()
                .id(1L)
                .domain("example.com")
                .build();

        testRecord = DnsRecord.builder()
                .id(1L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.A)
                .name("@")
                .content("192.168.1.1")
                .ttl(3600)
                .priority(null)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .syncStatus(DnsRecord.SyncStatus.SYNCED)
                .build();
    }

    // ==================== Update Record Tests ====================

    @Test
    @DisplayName("Should update DNS record successfully")
    void testUpdateRecordSuccess() {
        // Arrange
        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.A)
                .name("www")
                .content("192.168.1.2")
                .ttl(7200)
                .priority(null)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .build();

        DnsRecord updatedRecord = DnsRecord.builder()
                .id(1L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.A)
                .name("www")
                .content("192.168.1.2")
                .ttl(7200)
                .priority(null)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .syncStatus(DnsRecord.SyncStatus.PENDING) // Auto-set to PENDING on update
                .build();

        when(dnsRecordRepository.findById(1L)).thenReturn(Optional.of(testRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenReturn(updatedRecord);

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(1L, updates))
                .expectNext(updatedRecord)
                .verifyComplete();

        verify(dnsRecordRepository).findById(1L);
        verify(dnsRecordRepository).save(argThat(record ->
                record.getSyncStatus() == DnsRecord.SyncStatus.PENDING));
    }

    @Test
    @DisplayName("Should update MX record with priority successfully")
    void testUpdateMxRecordWithPriority() {
        // Arrange
        DnsRecord mxRecord = DnsRecord.builder()
                .id(2L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.MX)
                .name("@")
                .content("mail.example.com")
                .ttl(3600)
                .priority(10)
                .purpose(DnsRecord.RecordPurpose.MX)
                .syncStatus(DnsRecord.SyncStatus.SYNCED)
                .build();

        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.MX)
                .name("@")
                .content("mail2.example.com")
                .ttl(3600)
                .priority(20)
                .purpose(DnsRecord.RecordPurpose.MX)
                .build();

        DnsRecord updatedRecord = DnsRecord.builder()
                .id(2L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.MX)
                .name("@")
                .content("mail2.example.com")
                .ttl(3600)
                .priority(20)
                .purpose(DnsRecord.RecordPurpose.MX)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        when(dnsRecordRepository.findById(2L)).thenReturn(Optional.of(mxRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenReturn(updatedRecord);

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(2L, updates))
                .expectNext(updatedRecord)
                .verifyComplete();

        verify(dnsRecordRepository).save(argThat(record ->
                record.getPriority() == 20 &&
                record.getSyncStatus() == DnsRecord.SyncStatus.PENDING));
    }

    @Test
    @DisplayName("Should update TXT record for SPF successfully")
    void testUpdateTxtRecordForSpf() {
        // Arrange
        DnsRecord txtRecord = DnsRecord.builder()
                .id(3L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.TXT)
                .name("@")
                .content("v=spf1 include:_spf.example.com ~all")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.SPF)
                .syncStatus(DnsRecord.SyncStatus.SYNCED)
                .build();

        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.TXT)
                .name("@")
                .content("v=spf1 include:_spf.example.com include:_spf.backup.com ~all")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.SPF)
                .build();

        DnsRecord updatedRecord = DnsRecord.builder()
                .id(3L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.TXT)
                .name("@")
                .content("v=spf1 include:_spf.example.com include:_spf.backup.com ~all")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.SPF)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        when(dnsRecordRepository.findById(3L)).thenReturn(Optional.of(txtRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenReturn(updatedRecord);

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(3L, updates))
                .expectNext(updatedRecord)
                .verifyComplete();

        verify(dnsRecordRepository).save(argThat(record ->
                record.getPurpose() == DnsRecord.RecordPurpose.SPF));
    }

    @Test
    @DisplayName("Should update DMARC record successfully")
    void testUpdateDmarcRecord() {
        // Arrange
        DnsRecord dmarcRecord = DnsRecord.builder()
                .id(4L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.TXT)
                .name("_dmarc")
                .content("v=DMARC1; p=quarantine; rua=mailto:dmarc@example.com")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.DMARC)
                .syncStatus(DnsRecord.SyncStatus.SYNCED)
                .build();

        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.TXT)
                .name("_dmarc")
                .content("v=DMARC1; p=reject; rua=mailto:dmarc@example.com; pct=100")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.DMARC)
                .build();

        DnsRecord updatedRecord = DnsRecord.builder()
                .id(4L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.TXT)
                .name("_dmarc")
                .content("v=DMARC1; p=reject; rua=mailto:dmarc@example.com; pct=100")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.DMARC)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        when(dnsRecordRepository.findById(4L)).thenReturn(Optional.of(dmarcRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenReturn(updatedRecord);

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(4L, updates))
                .expectNext(updatedRecord)
                .verifyComplete();

        verify(dnsRecordRepository).save(argThat(record ->
                record.getPurpose() == DnsRecord.RecordPurpose.DMARC &&
                record.getContent().contains("p=reject")));
    }

    @Test
    @DisplayName("Should update DKIM record successfully")
    void testUpdateDkimRecord() {
        // Arrange
        DnsRecord dkimRecord = DnsRecord.builder()
                .id(5L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.TXT)
                .name("default._domainkey")
                .content("v=DKIM1; k=rsa; p=MIGfMA0GCSqGSI...")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.DKIM)
                .syncStatus(DnsRecord.SyncStatus.SYNCED)
                .build();

        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.TXT)
                .name("default._domainkey")
                .content("v=DKIM1; k=rsa; p=NEWKEYNEWKEY...")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.DKIM)
                .build();

        DnsRecord updatedRecord = DnsRecord.builder()
                .id(5L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.TXT)
                .name("default._domainkey")
                .content("v=DKIM1; k=rsa; p=NEWKEYNEWKEY...")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.DKIM)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        when(dnsRecordRepository.findById(5L)).thenReturn(Optional.of(dkimRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenReturn(updatedRecord);

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(5L, updates))
                .expectNext(updatedRecord)
                .verifyComplete();

        verify(dnsRecordRepository).save(argThat(record ->
                record.getPurpose() == DnsRecord.RecordPurpose.DKIM));
    }

    @Test
    @DisplayName("Should update record and reset sync status to PENDING")
    void testUpdateRecordResetsSyncStatus() {
        // Arrange
        testRecord.setSyncStatus(DnsRecord.SyncStatus.SYNCED);

        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.A)
                .name("@")
                .content("192.168.1.99")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .build();

        when(dnsRecordRepository.findById(1L)).thenReturn(Optional.of(testRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(1L, updates))
                .expectNextMatches(record ->
                        record.getSyncStatus() == DnsRecord.SyncStatus.PENDING)
                .verifyComplete();

        verify(dnsRecordRepository).save(argThat(record ->
                record.getSyncStatus() == DnsRecord.SyncStatus.PENDING));
    }

    @Test
    @DisplayName("Should reject updating non-existent record")
    void testUpdateRecordNotFound() {
        // Arrange
        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.A)
                .name("@")
                .content("192.168.1.1")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .build();

        when(dnsRecordRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(999L, updates))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("DNS Record not found: 999"))
                .verify();

        verify(dnsRecordRepository).findById(999L);
        verify(dnsRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle repository error during update")
    void testUpdateRecordRepositoryError() {
        // Arrange
        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.A)
                .name("@")
                .content("192.168.1.1")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .build();

        when(dnsRecordRepository.findById(1L)).thenReturn(Optional.of(testRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(1L, updates))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Database connection failed"))
                .verify();
    }

    @Test
    @DisplayName("Should update AAAA record successfully")
    void testUpdateAaaaRecord() {
        // Arrange
        DnsRecord aaaaRecord = DnsRecord.builder()
                .id(6L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.AAAA)
                .name("@")
                .content("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .syncStatus(DnsRecord.SyncStatus.SYNCED)
                .build();

        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.AAAA)
                .name("@")
                .content("2001:0db8:85a3:0000:0000:8a2e:0370:9999")
                .ttl(7200)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .build();

        DnsRecord updatedRecord = DnsRecord.builder()
                .id(6L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.AAAA)
                .name("@")
                .content("2001:0db8:85a3:0000:0000:8a2e:0370:9999")
                .ttl(7200)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        when(dnsRecordRepository.findById(6L)).thenReturn(Optional.of(aaaaRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenReturn(updatedRecord);

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(6L, updates))
                .expectNext(updatedRecord)
                .verifyComplete();

        verify(dnsRecordRepository).save(argThat(record ->
                record.getType() == DnsRecord.RecordType.AAAA));
    }

    @Test
    @DisplayName("Should update CNAME record successfully")
    void testUpdateCnameRecord() {
        // Arrange
        DnsRecord cnameRecord = DnsRecord.builder()
                .id(7L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.CNAME)
                .name("www")
                .content("example.com")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .syncStatus(DnsRecord.SyncStatus.SYNCED)
                .build();

        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.CNAME)
                .name("www")
                .content("cdn.example.com")
                .ttl(7200)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .build();

        DnsRecord updatedRecord = DnsRecord.builder()
                .id(7L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.CNAME)
                .name("www")
                .content("cdn.example.com")
                .ttl(7200)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        when(dnsRecordRepository.findById(7L)).thenReturn(Optional.of(cnameRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenReturn(updatedRecord);

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(7L, updates))
                .expectNext(updatedRecord)
                .verifyComplete();

        verify(dnsRecordRepository).save(argThat(record ->
                record.getType() == DnsRecord.RecordType.CNAME));
    }

    // ==================== Delete Record Tests ====================

    @Test
    @DisplayName("Should delete DNS record successfully")
    void testDeleteRecordSuccess() {
        // Arrange
        doNothing().when(dnsRecordRepository).deleteById(1L);

        // Act & Assert
        StepVerifier.create(dnsRecordService.deleteRecord(1L))
                .verifyComplete();

        verify(dnsRecordRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Should handle repository error during deletion")
    void testDeleteRecordRepositoryError() {
        // Arrange
        doThrow(new RuntimeException("Database error"))
                .when(dnsRecordRepository).deleteById(1L);

        // Act & Assert
        StepVerifier.create(dnsRecordService.deleteRecord(1L))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Database error"))
                .verify();

        verify(dnsRecordRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Should delete record without checking existence")
    void testDeleteRecordWithoutExistenceCheck() {
        // Arrange - Service doesn't check if record exists before deleting
        doNothing().when(dnsRecordRepository).deleteById(999L);

        // Act & Assert - Should complete successfully even if record doesn't exist
        StepVerifier.create(dnsRecordService.deleteRecord(999L))
                .verifyComplete();

        verify(dnsRecordRepository).deleteById(999L);
    }

    @Test
    @DisplayName("Should handle concurrent update operations")
    void testConcurrentUpdateOperations() {
        // Arrange
        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.A)
                .name("@")
                .content("192.168.1.100")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.OTHER)
                .build();

        when(dnsRecordRepository.findById(1L)).thenReturn(Optional.of(testRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Simulate concurrent updates
        var update1 = dnsRecordService.updateRecord(1L, updates);
        var update2 = dnsRecordService.updateRecord(1L, updates);
        var update3 = dnsRecordService.updateRecord(1L, updates);

        // Assert - All should complete successfully
        StepVerifier.create(reactor.core.publisher.Flux.merge(update1, update2, update3))
                .expectNextCount(3)
                .verifyComplete();

        verify(dnsRecordRepository, times(3)).findById(1L);
        verify(dnsRecordRepository, times(3)).save(any(DnsRecord.class));
    }

    @Test
    @DisplayName("Should update NS record successfully")
    void testUpdateNsRecord() {
        // Arrange
        DnsRecord nsRecord = DnsRecord.builder()
                .id(8L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.NS)
                .name("@")
                .content("ns1.example.com")
                .ttl(86400)
                .purpose(DnsRecord.RecordPurpose.NS)
                .syncStatus(DnsRecord.SyncStatus.SYNCED)
                .build();

        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.NS)
                .name("@")
                .content("ns2.example.com")
                .ttl(86400)
                .purpose(DnsRecord.RecordPurpose.NS)
                .build();

        DnsRecord updatedRecord = DnsRecord.builder()
                .id(8L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.NS)
                .name("@")
                .content("ns2.example.com")
                .ttl(86400)
                .purpose(DnsRecord.RecordPurpose.NS)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        when(dnsRecordRepository.findById(8L)).thenReturn(Optional.of(nsRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenReturn(updatedRecord);

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(8L, updates))
                .expectNext(updatedRecord)
                .verifyComplete();

        verify(dnsRecordRepository).save(argThat(record ->
                record.getPurpose() == DnsRecord.RecordPurpose.NS));
    }

    @Test
    @DisplayName("Should update SRV record successfully")
    void testUpdateSrvRecord() {
        // Arrange
        DnsRecord srvRecord = DnsRecord.builder()
                .id(9L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.SRV)
                .name("_xmpp._tcp")
                .content("xmpp.example.com")
                .ttl(3600)
                .priority(5)
                .purpose(DnsRecord.RecordPurpose.SERVICE_DISCOVERY)
                .syncStatus(DnsRecord.SyncStatus.SYNCED)
                .build();

        DnsRecord updates = DnsRecord.builder()
                .type(DnsRecord.RecordType.SRV)
                .name("_xmpp._tcp")
                .content("xmpp2.example.com")
                .ttl(3600)
                .priority(10)
                .purpose(DnsRecord.RecordPurpose.SERVICE_DISCOVERY)
                .build();

        DnsRecord updatedRecord = DnsRecord.builder()
                .id(9L)
                .domain(testDomain)
                .type(DnsRecord.RecordType.SRV)
                .name("_xmpp._tcp")
                .content("xmpp2.example.com")
                .ttl(3600)
                .priority(10)
                .purpose(DnsRecord.RecordPurpose.SERVICE_DISCOVERY)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build();

        when(dnsRecordRepository.findById(9L)).thenReturn(Optional.of(srvRecord));
        when(dnsRecordRepository.save(any(DnsRecord.class))).thenReturn(updatedRecord);

        // Act & Assert
        StepVerifier.create(dnsRecordService.updateRecord(9L, updates))
                .expectNext(updatedRecord)
                .verifyComplete();

        verify(dnsRecordRepository).save(argThat(record ->
                record.getType() == DnsRecord.RecordType.SRV &&
                record.getPurpose() == DnsRecord.RecordPurpose.SERVICE_DISCOVERY));
    }
}
