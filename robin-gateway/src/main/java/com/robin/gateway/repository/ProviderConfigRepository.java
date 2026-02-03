package com.robin.gateway.repository;

import com.robin.gateway.model.ProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, Long> {
}
