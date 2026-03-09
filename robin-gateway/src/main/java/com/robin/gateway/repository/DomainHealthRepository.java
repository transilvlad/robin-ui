package com.robin.gateway.repository;

import com.robin.gateway.model.DomainCheckType;
import com.robin.gateway.model.DomainHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DomainHealthRepository extends JpaRepository<DomainHealth, Long> {
    List<DomainHealth> findByDomainId(Long domainId);
    Optional<DomainHealth> findByDomainIdAndCheckType(Long domainId, DomainCheckType checkType);
}
