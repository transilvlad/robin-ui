package com.robin.gateway.repository;

import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.DnsProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DnsProviderRepository extends JpaRepository<DnsProvider, Long> {
    Optional<DnsProvider> findByName(String name);
    Optional<DnsProvider> findByType(DnsProviderType type);
}
