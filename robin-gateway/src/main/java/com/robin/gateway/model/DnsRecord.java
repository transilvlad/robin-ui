package com.robin.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dns_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Domain domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecordType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer ttl;

    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RecordPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", length = 20)
    private SyncStatus syncStatus;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonProperty("domainId")
    public Long getDomainId() {
        return domain != null ? domain.getId() : null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum RecordType {
        MX, TXT, CNAME, A, AAAA, TLSA, NS, DS, PTR, SRV
    }

    public enum RecordPurpose {
        DKIM, SPF, DMARC, MTA_STS_RECORD, MTA_STS_POLICY_HOST, DANE, BIMI, DNSSEC, NS, VERIFICATION, MX, SERVICE_DISCOVERY, OTHER
    }

    public enum SyncStatus {
        PENDING, SYNCED, ERROR
    }
}
