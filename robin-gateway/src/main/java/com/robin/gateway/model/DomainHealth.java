package com.robin.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "domain_health", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"domain_id", "check_type"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainHealth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 20)
    private DomainCheckType checkType; // SPF, DKIM, DMARC, MTA_STS, MX, NS

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DomainHealthStatus status; // OK, WARN, ERROR, UNKNOWN

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "last_checked", nullable = false)
    @Builder.Default
    private LocalDateTime lastChecked = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastChecked = LocalDateTime.now();
    }
}
