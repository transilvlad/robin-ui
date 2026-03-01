package com.robin.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "mta_sts_workers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtaStsWorker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domain_id", nullable = false, unique = true)
    private Long domainId;

    @Column(name = "worker_name", nullable = false)
    private String workerName;

    @Column(name = "worker_id")
    private String workerId; // Cloudflare Worker script ID

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_mode", length = 20)
    @Builder.Default
    private MtaStsPolicyMode policyMode = MtaStsPolicyMode.testing;

    @Column(name = "policy_version", length = 50)
    private String policyVersion;

    @Column(name = "deployed_at")
    private LocalDateTime deployedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private MtaStsWorkerStatus status = MtaStsWorkerStatus.PENDING; // PENDING, DEPLOYED, ERROR
}
