package com.robin.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "dkim_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DkimKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    private Domain domain;

    @Column(nullable = false, length = 50)
    private String selector;

    @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
    private String privateKey; // Encrypted PEM

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey; // PEM or raw base64

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private DkimStatus status = DkimStatus.STANDBY;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum DkimStatus {
        ACTIVE, STANDBY, DEPRECATED
    }
}
