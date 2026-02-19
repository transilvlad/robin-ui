package com.robin.gateway.service.dns;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@Slf4j
public class ManualDnsProvider implements DnsProvider {

    @Override
    public List<DnsRecord> listRecords(Domain domain) {
        return List.of();
    }

    @Override
    public void createRecord(Domain domain, DnsRecord record) {
        log.info("Manual DNS: record generation requested for {} {}", record.getType(), record.getName());
    }

    @Override
    public void updateRecord(Domain domain, DnsRecord record) {
        log.info("Manual DNS: update requested for {}", record.getExternalId());
    }

    @Override
    public void deleteRecord(Domain domain, String externalId) {
        log.info("Manual DNS: delete requested for {}", externalId);
    }

    @Override
    public void enableDnssec(Domain domain) {
        log.info("Manual DNS: user must enable DNSSEC manually");
    }

    @Override
    public void disableDnssec(Domain domain) {
        log.info("Manual DNS: user must disable DNSSEC manually");
    }

    @Override
    public List<DnsRecord> getDsRecords(Domain domain) {
        return List.of();
    }
}
