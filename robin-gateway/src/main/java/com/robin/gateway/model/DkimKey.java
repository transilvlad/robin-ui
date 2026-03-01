package com.robin.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dkim_keys", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"domain_id", "selector"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DkimKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Column(nullable = false)
    private String selector;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DkimAlgorithm algorithm;

    @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
    private String privateKey; // AES-256 encrypted

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "cname_selector")
    private String cnameSelector; // for CNAME rotation: points to this selector

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private DkimKeyStatus status = DkimKeyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
