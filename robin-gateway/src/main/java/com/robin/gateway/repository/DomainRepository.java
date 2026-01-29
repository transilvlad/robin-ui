package com.robin.gateway.repository;

import com.robin.gateway.model.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DomainRepository extends JpaRepository<Domain, Long> {
    Optional<Domain> findByDomain(String domain);
    boolean existsByDomain(String domain);
}
