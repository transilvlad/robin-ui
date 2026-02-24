package com.robin.gateway.repository;

import com.robin.gateway.model.MtaStsWorker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MtaStsWorkerRepository extends JpaRepository<MtaStsWorker, Long> {
    Optional<MtaStsWorker> findByDomainId(Long domainId);
}
