package com.robin.gateway.repository;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DnsRecordRepository extends JpaRepository<DnsRecord, Long> {
    List<DnsRecord> findByDomain(Domain domain);
    List<DnsRecord> findByDomainId(Long domainId);
    void deleteByDomain(Domain domain);
}
