package com.robin.gateway.repository;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DnsRecordRepository extends JpaRepository<DnsRecord, Long> {
    List<DnsRecord> findByDomain(Domain domain);
    List<DnsRecord> findByDomain_Id(Long domainId);
    
    @Transactional
    void deleteByDomain(Domain domain);

    @Modifying
    @Transactional
    @Query("DELETE FROM DnsRecord d WHERE d.domain.id = :domainId")
    void deleteByDomain_Id(@Param("domainId") Long domainId);

    @Modifying
    @Transactional
    @Query("UPDATE DnsRecord d SET d.purpose = 'SERVICE_DISCOVERY' WHERE d.purpose = 'NS' AND (d.type = 'SRV' OR d.name LIKE 'autoconfig%' OR d.name LIKE 'autodiscover%')")
    int updateMisclassifiedPurposes();
}
