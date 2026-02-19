package com.robin.gateway.service;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class DnsDiscoveryService {

    private final DnsRecordGenerator dnsRecordGenerator;
    private final com.robin.gateway.repository.ProviderConfigRepository providerRepository;
    private final com.robin.gateway.service.dns.DnsProviderFactory dnsProviderFactory;
    private final com.robin.gateway.repository.DomainRepository domainRepository;

    public Mono<DiscoveryResult> discover(String domainName, Long dnsProviderId) {
        return Mono.fromCallable(() -> {
            // Early exit if already exists
            if (domainRepository.existsByDomain(domainName)) {
                 throw new IllegalArgumentException("Domain already exists in Robin: " + domainName);
            }

            Domain partialConfig = new Domain();
            partialConfig.setDomain(domainName);
            List<DnsRecord> discovered = new ArrayList<>();

            if (dnsProviderId != null) {
                // API based discovery
                log.info("Starting API discovery for {} using provider ID: {}", domainName, dnsProviderId);
                Optional<com.robin.gateway.model.ProviderConfig> providerOpt = providerRepository.findById(dnsProviderId);
                
                if (providerOpt.isPresent()) {
                    com.robin.gateway.model.ProviderConfig p = providerOpt.get();
                    log.info("Found provider: {} (Type: {})", p.getName(), p.getType());
                    partialConfig.setDnsProvider(p);
                    partialConfig.setDnsProviderType(Domain.DnsProviderType.valueOf(p.getType().name()));
                    try {
                        com.robin.gateway.service.dns.DnsProvider provider = dnsProviderFactory.getProvider(partialConfig.getDnsProviderType());
                        List<DnsRecord> records = provider.listRecords(partialConfig);
                        log.info("Provider listRecords returned {} total records", records.size());
                        
                        // Filter records for this specific domain (in case of Cloudflare account-wide listing)
                        records.stream()
                                .filter(r -> {
                                    boolean match = r.getName().contains(domainName) || r.getName().equals("@");
                                    if (match) log.debug("Matched API record: {} {} {}", r.getType(), r.getName(), r.getContent());
                                    return match;
                                })
                                .forEach(r -> {
                                    discovered.add(r);
                                    // Also parse SPF/DMARC if found in API records
                                    if (r.getType() == DnsRecord.RecordType.TXT) {
                                        String val = r.getContent().replaceAll("^\"|\"$", "");
                                        if (val.startsWith("v=spf1")) {
                                            log.info("Detected SPF from API: {}", val);
                                            parseSpf(val, partialConfig);
                                        } else if (val.startsWith("v=DMARC1")) {
                                            log.info("Detected DMARC from API: {}", val);
                                            parseDmarc(val, partialConfig);
                                        }
                                    }
                                });
                        log.info("API discovery successfully filtered {} records for {}", discovered.size(), domainName);
                    } catch (Exception e) {
                        log.error("API discovery failed for {}", domainName, e);
                    }
                } else {
                    log.warn("Provider with ID {} not found in database", dnsProviderId);
                }
            } else {
                log.info("No DNS provider ID provided for {}, falling back to public discovery only", domainName);
            }

            // Only perform public lookup if we didn't use a provider OR to supplement (if provider failed/returned nothing)
            if (discovered.isEmpty()) {
                log.info("No records found via API (or no API used), performing public lookup for {}", domainName);
                List<DnsRecord> publicRecords = performPublicLookup(domainName, partialConfig);
                discovered.addAll(publicRecords);
                log.info("Public lookup found {} records", publicRecords.size());
            } else {
                log.info("Skipping public discovery as {} API records were found for {}", discovered.size(), domainName);
            }

            // Generate proposed records based on the detected (or default) configuration
            List<DnsRecord> proposed = dnsRecordGenerator.generateExpectedRecords(partialConfig);

            return new DiscoveryResult(discovered, proposed, partialConfig);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private List<DnsRecord> performPublicLookup(String domainName, Domain partialConfig) {
        List<DnsRecord> discovered = new ArrayList<>();
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns://8.8.8.8"); // Use Google DNS for consistency
            DirContext ctx = new InitialDirContext(env);

            log.info("Starting public DNS discovery for {} using 8.8.8.8", domainName);

            // 1. Root Records (@)
            discovered.addAll(lookup(ctx, domainName, domainName, "MX"));
            discovered.addAll(lookup(ctx, domainName, domainName, "A"));
            discovered.addAll(lookup(ctx, domainName, domainName, "AAAA"));
            List<DnsRecord> txtRecords = lookup(ctx, domainName, domainName, "TXT");
            discovered.addAll(txtRecords);

            for (DnsRecord rec : txtRecords) {
                String val = rec.getContent().replaceAll("^\"|\"$", "");
                if (val.startsWith("v=spf1")) {
                    rec.setPurpose(DnsRecord.RecordPurpose.SPF);
                    parseSpf(val, partialConfig);
                }
            }

            // 2. Specific Subdomains
            discovered.addAll(lookup(ctx, domainName, "mail." + domainName, "A"));
            discovered.addAll(lookup(ctx, domainName, "mail." + domainName, "AAAA"));
            discovered.addAll(lookup(ctx, domainName, "autoconfig." + domainName, "CNAME"));
            discovered.addAll(lookup(ctx, domainName, "autodiscover." + domainName, "CNAME"));

            // 3. DMARC
            List<DnsRecord> dmarcRecords = lookup(ctx, domainName, "_dmarc." + domainName, "TXT");
            discovered.addAll(dmarcRecords);
            for (DnsRecord rec : dmarcRecords) {
                rec.setPurpose(DnsRecord.RecordPurpose.DMARC);
                parseDmarc(rec.getContent().replaceAll("^\"|\"$", ""), partialConfig);
            }

            // 4. DKIM (robin standard selectors)
            String[] dkimSelectors = {"robin1", "robin2", "robin3"};
            for (String selector : dkimSelectors) {
                List<DnsRecord> dkimRecords = lookup(ctx, domainName, selector + "._domainkey." + domainName, "TXT");
                dkimRecords.forEach(r -> r.setPurpose(DnsRecord.RecordPurpose.DKIM));
                discovered.addAll(dkimRecords);
            }

            // 5. MTA-STS/SRV
            discovered.addAll(lookup(ctx, domainName, "_smtp._tls." + domainName, "TXT"));
            discovered.addAll(lookup(ctx, domainName, "_mta-sts." + domainName, "TXT"));
            discovered.addAll(lookup(ctx, domainName, "mta-sts." + domainName, "A"));
            
            String[] srvs = {"_submission._tcp", "_imaps._tcp", "_pop3s._tcp", "_imap._tcp"};
            for (String srv : srvs) discovered.addAll(lookup(ctx, domainName, srv + "." + domainName, "SRV"));

        } catch (Exception e) {
            log.error("Public DNS discovery failed for {}", domainName, e);
        }
        return discovered;
    }

    public Mono<DiscoveryResult> discover(String domainName) {
        return discover(domainName, null);
    }

    private List<DnsRecord> lookup(DirContext ctx, String rootDomain, String lookupName, String type) {
        List<DnsRecord> results = new ArrayList<>();
        try {
            Attributes attrs = ctx.getAttributes(lookupName, new String[]{type});
            if (attrs != null) {
                Attribute attr = attrs.get(type);
                if (attr != null) {
                    NamingEnumeration<?> en = attr.getAll();
                    while (en.hasMore()) {
                        String value = (String) en.next();
                        DnsRecord.DnsRecordBuilder builder = DnsRecord.builder()
                                .name(lookupName.equals(rootDomain) ? "@" : lookupName.replace("." + rootDomain, ""))
                                .content(value)
                                .ttl(300)
                                .syncStatus(DnsRecord.SyncStatus.SYNCED);

                        switch (type) {
                            case "MX":
                                builder.type(DnsRecord.RecordType.MX); builder.purpose(DnsRecord.RecordPurpose.MX);
                                String[] p = value.split("\\s+");
                                if (p.length > 1) { builder.priority(Integer.parseInt(p[0])); builder.content(p[1]); }
                                break;
                            case "A":
                                builder.type(DnsRecord.RecordType.A); builder.purpose(DnsRecord.RecordPurpose.MX);
                                if (lookupName.startsWith("mta-sts.")) builder.purpose(DnsRecord.RecordPurpose.MTA_STS_POLICY_HOST);
                                break;
                            case "AAAA":
                                builder.type(DnsRecord.RecordType.AAAA); builder.purpose(DnsRecord.RecordPurpose.MX);
                                break;
                            case "CNAME":
                                builder.type(DnsRecord.RecordType.CNAME); builder.purpose(DnsRecord.RecordPurpose.SERVICE_DISCOVERY);
                                break;
                            case "TXT":
                                builder.type(DnsRecord.RecordType.TXT); builder.purpose(DnsRecord.RecordPurpose.VERIFICATION);
                                break;
                            case "SRV":
                                builder.type(DnsRecord.RecordType.SRV); builder.purpose(DnsRecord.RecordPurpose.SERVICE_DISCOVERY);
                                break;
                        }
                        results.add(builder.build());
                    }
                }
            }
        } catch (Exception e) {}
        return results;
    }

    private void parseSpf(String spf, Domain d) {
        d.setSpfSoftFail(spf.contains("~all"));
        List<String> incs = new ArrayList<>();
        Matcher m = Pattern.compile("include:([^\\s]+)").matcher(spf);
        while (m.find()) incs.add(m.group(1));
        if (!incs.isEmpty()) d.setSpfIncludes(String.join(",", incs));
    }

    private void parseDmarc(String dmarc, Domain d) {
        Map<String, String> tags = new HashMap<>();
        for (String part : dmarc.split(";")) {
            String[] kv = part.trim().split("=");
            if (kv.length == 2) tags.put(kv[0].trim(), kv[1].trim());
        }
        if (tags.containsKey("p")) d.setDmarcPolicy(tags.get("p"));
        if (tags.containsKey("sp")) d.setDmarcSubdomainPolicy(tags.get("sp"));
        if (tags.containsKey("pct")) try { d.setDmarcPercentage(Integer.parseInt(tags.get("pct"))); } catch (Exception e) {}
        if (tags.containsKey("adkim")) d.setDmarcAlignment(tags.get("adkim"));
        if (tags.containsKey("rua")) {
             String r = tags.get("rua");
             if (r.startsWith("mailto:")) r = r.substring(7);
             d.setDmarcReportingEmail(r);
        }
    }

    @lombok.Data @lombok.AllArgsConstructor @lombok.NoArgsConstructor
    public static class DiscoveryResult {
        private List<DnsRecord> discoveredRecords;
        private List<DnsRecord> proposedRecords;
        private Domain configuration;
    }
}
