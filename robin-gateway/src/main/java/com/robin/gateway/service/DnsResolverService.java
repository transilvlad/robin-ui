package com.robin.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xbill.DNS.*;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DnsResolverService {

    public List<String> resolveTxtRecords(String domain) {
        return resolveRecords(domain, Type.TXT).stream()
                .map(record -> String.join("", ((TXTRecord) record).getStrings()))
                .toList();
    }

    public List<String> resolveARecords(String domain) {
        return resolveRecords(domain, Type.A).stream()
                .map(record -> ((ARecord) record).getAddress().getHostAddress())
                .toList();
    }

    public List<String> resolveMxRecords(String domain) {
        return resolveRecords(domain, Type.MX).stream()
                .map(record -> {
                    MXRecord mx = (MXRecord) record;
                    return mx.getPriority() + " " + mx.getTarget().toString(true);
                })
                .toList();
    }

    public List<String> resolveCnameRecords(String domain) {
        return resolveRecords(domain, Type.CNAME).stream()
                .map(record -> ((CNAMERecord) record).getTarget().toString(true))
                .toList();
    }

    public List<String> resolveNsRecords(String domain) {
        return resolveRecords(domain, Type.NS).stream()
                .map(record -> ((NSRecord) record).getTarget().toString(true))
                .toList();
    }

    private List<org.xbill.DNS.Record> resolveRecords(String domain, int type) {
        List<org.xbill.DNS.Record> results = new ArrayList<>();
        try {
            Lookup lookup = new Lookup(domain, type);
            lookup.run();

            if (lookup.getResult() == Lookup.SUCCESSFUL) {
                org.xbill.DNS.Record[] records = lookup.getAnswers();
                if (records != null) {
                    for (org.xbill.DNS.Record record : records) {
                        results.add(record);
                    }
                }
            } else {
                log.debug("DNS lookup failed for {} (type {}): {}", domain, Type.string(type), lookup.getErrorString());
            }
        } catch (TextParseException e) {
            log.error("Invalid domain name {}: {}", domain, e.getMessage());
        }
        return results;
    }
}
