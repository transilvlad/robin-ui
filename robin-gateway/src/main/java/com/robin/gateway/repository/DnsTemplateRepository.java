package com.robin.gateway.repository;

import com.robin.gateway.model.DnsTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DnsTemplateRepository extends JpaRepository<DnsTemplate, Long> {
    Optional<DnsTemplate> findByName(String name);
}
