package com.robin.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dkim_detected_selectors", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"domain", "selector"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DkimDetectedSelector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String domain;

    @Column(nullable = false)
    private String selector;

    @Column(name = "public_key_dns", columnDefinition = "TEXT")
    private String publicKeyDns;

    @Column(length = 10)
    private String algorithm;

    @Column(name = "test_mode")
    private Boolean testMode;

    @Builder.Default
    private boolean revoked = false;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @PrePersist
    protected void onCreate() {
        detectedAt = LocalDateTime.now();
    }
}
