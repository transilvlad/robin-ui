package com.robin.gateway.repository;

import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.DkimKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DkimKeyRepository extends JpaRepository<DkimKey, Long> {
    List<DkimKey> findByDomainId(Long domainId);
    List<DkimKey> findByDomainIdAndStatus(Long domainId, DkimKeyStatus status);
    Optional<DkimKey> findByDomainIdAndSelector(Long domainId, String selector);
}
