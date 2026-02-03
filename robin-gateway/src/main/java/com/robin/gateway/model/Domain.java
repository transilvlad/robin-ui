package com.robin.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "domains")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Domain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private DomainStatus status = DomainStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "dns_provider_type", length = 20)
    @Builder.Default
    private DnsProviderType dnsProviderType = DnsProviderType.MANUAL;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dns_provider_id")
    private ProviderConfig dnsProvider;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "registrar_provider_type", length = 20)
    @Builder.Default
    private RegistrarProviderType registrarProviderType = RegistrarProviderType.NONE;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registrar_provider_id")
    private ProviderConfig registrarProvider;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @Column(name = "nameservers", columnDefinition = "TEXT")
    private String nameservers; // JSON Array

    @Column(name = "dnssec_enabled")
    @Builder.Default
    private Boolean dnssecEnabled = false;

    @Column(name = "mta_sts_enabled")
    @Builder.Default
    private Boolean mtaStsEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "mta_sts_mode", length = 20)
    @Builder.Default
    private MtaStsMode mtaStsMode = MtaStsMode.NONE;

    @Column(name = "dane_enabled")
    @Builder.Default
    private Boolean daneEnabled = false;

    @Column(name = "bimi_selector")
    private String bimiSelector;

    @Column(name = "bimi_logo_url")
    private String bimiLogoUrl;

    @Column(name = "dkim_selector_prefix")
    @Builder.Default
    private String dkimSelectorPrefix = "robin";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum DomainStatus {
        PENDING, VERIFIED, FAILED, ACTIVE
    }

    public enum DnsProviderType {
        MANUAL, CLOUDFLARE, AWS_ROUTE53
    }
    
    public enum RegistrarProviderType {
        NONE, MANUAL, CLOUDFLARE, AWS_ROUTE53, GODADDY
    }
    
    public enum MtaStsMode {
        NONE, TESTING, ENFORCE
    }
}