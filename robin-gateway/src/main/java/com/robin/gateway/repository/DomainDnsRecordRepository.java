package com.robin.gateway.repository;

import com.robin.gateway.model.DomainDnsRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DomainDnsRecordRepository extends JpaRepository<DomainDnsRecord, Long> {
    List<DomainDnsRecord> findByDomainId(Long domainId);
    List<DomainDnsRecord> findByDomainIdAndRecordType(Long domainId, String recordType);
}
