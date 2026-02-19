package com.robin.gateway.service.dns;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import java.util.List;

public interface DnsProvider {
    List<DnsRecord> listRecords(Domain domain);
    void createRecord(Domain domain, DnsRecord record);
    void updateRecord(Domain domain, DnsRecord record);
    void deleteRecord(Domain domain, String externalId);
    
    // DNSSEC management
    void enableDnssec(Domain domain);
    void disableDnssec(Domain domain);
    List<DnsRecord> getDsRecords(Domain domain);
}
