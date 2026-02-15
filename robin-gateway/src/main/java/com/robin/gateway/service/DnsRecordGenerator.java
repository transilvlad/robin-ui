package com.robin.gateway.service;

import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DnsRecordGenerator {

    private final DkimService dkimService;
    private final CertService certService;
    private final ConfigurationService configService;

    @Value("${robin.gateway.external-ip:127.0.0.1}")
    private String gatewayIp;

    @Value("${robin.mail.cert-path:/etc/ssl/certs/mail.pem}")
    private String certPath;

    public List<DnsRecord> generateExpectedRecords(Domain domain) {
        List<DnsRecord> records = new ArrayList<>();
        
        // Fetch global email reporting config
        Map<String, Object> config = null;
        try {
            config = configService.getConfig("email_reporting").block();
        } catch (Exception e) {
            log.warn("Failed to load email_reporting config, using defaults", e);
        }
        
        // Defaults
        String reportingEmail = "postmaster@" + domain.getDomain();
        String dmarcPolicy = "none";
        String dmarcSubdomainPolicy = "none";
        int dmarcPct = 100;
        String dmarcAlignment = "r";
        String spfIncludes = "";
        boolean spfSoftFail = true;

        if (config != null) {
            reportingEmail = (String) config.getOrDefault("reportingEmail", reportingEmail);
            
            // [GAP-006] Map access for JSON configuration sections is untyped
            @SuppressWarnings("unchecked")
            Map<String, Object> dmarc = (Map<String, Object>) config.get("dmarc");
            if (dmarc != null) {
                dmarcPolicy = (String) dmarc.getOrDefault("policy", dmarcPolicy);
                dmarcSubdomainPolicy = (String) dmarc.getOrDefault("subdomainPolicy", dmarcSubdomainPolicy);
                dmarcPct = dmarc.get("percentage") instanceof Number ? ((Number) dmarc.get("percentage")).intValue() : dmarcPct;
                dmarcAlignment = (String) dmarc.getOrDefault("alignment", dmarcAlignment);
            }

            // [GAP-006] Map access for JSON configuration sections is untyped
            @SuppressWarnings("unchecked")
            Map<String, Object> spf = (Map<String, Object>) config.get("spf");
            if (spf != null) {
                spfIncludes = (String) spf.getOrDefault("includes", spfIncludes);
                spfSoftFail = spf.get("softFail") instanceof Boolean ? (Boolean) spf.get("softFail") : spfSoftFail;
            }
        }

        // Override with domain-specific settings if present
        if (domain.getDmarcPolicy() != null) dmarcPolicy = domain.getDmarcPolicy();
        if (domain.getDmarcSubdomainPolicy() != null) dmarcSubdomainPolicy = domain.getDmarcSubdomainPolicy();
        if (domain.getDmarcPercentage() != null) dmarcPct = domain.getDmarcPercentage();
        if (domain.getDmarcAlignment() != null) dmarcAlignment = domain.getDmarcAlignment();
        if (domain.getDmarcReportingEmail() != null && !domain.getDmarcReportingEmail().isEmpty()) reportingEmail = domain.getDmarcReportingEmail();
        
        if (domain.getSpfIncludes() != null) {
            // Append or replace? Let's treat it as additional or override. 
            // Ideally we might want to merge, but simpler to just use domain config if set, or append to default if we want to enforce defaults.
            // Requirement was: "in the settings->reporting area keep them as well as default values for a new domain."
            // This implies global settings are defaults. So domain settings override.
            // But for SPF includes, maybe we want to keep global includes?
            // Let's assume domain settings fully override if provided, except maybe appending to global includes could be useful.
            // For now, simple override logic for scalars, append logic for includes could be better.
            
            // Let's go with: if domain has specific includes, use them + global includes? Or just them?
            // "spf configuration should be per domain" -> usually implies full control.
            // But typically a server has common includes (like its own relay).
            // Let's assume the Global Settings define the "System Default" and Domain Settings override specific fields.
            // For strings like 'includes', if the user puts something in domain settings, they probably want to ADD to the system defaults or REPLACE.
            // To be safe and flexible: If domain config is present, use it. The UI can pre-fill with defaults if needed.
            spfIncludes = domain.getSpfIncludes(); 
        }
        
        if (domain.getSpfSoftFail() != null) spfSoftFail = domain.getSpfSoftFail();

        // 0. A Record for Mail Host
        records.add(DnsRecord.builder()
                .domain(domain)
                .type(DnsRecord.RecordType.A)
                .name("mail")
                .content(gatewayIp)
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.MX) // Using MX purpose as it supports the MX record
                .build());

        // 1. MX Record
        records.add(DnsRecord.builder()
                .domain(domain)
                .type(DnsRecord.RecordType.MX)
                .name("@")
                .content("mail." + domain.getDomain() + ".")
                .priority(10)
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.MX)
                .build());

        // 2. SPF Record
        StringBuilder spfContent = new StringBuilder("v=spf1 mx");
        if (gatewayIp != null && !gatewayIp.isEmpty() && !gatewayIp.equals("127.0.0.1")) {
             spfContent.append(" ip4:").append(gatewayIp);
        }
        if (spfIncludes != null && !spfIncludes.trim().isEmpty()) {
            for (String inc : spfIncludes.split(",")) {
                String trimmed = inc.trim();
                if (!trimmed.isEmpty()) {
                    if (!trimmed.startsWith("include:")) {
                        spfContent.append(" include:");
                    } else {
                        spfContent.append(" ");
                    }
                    spfContent.append(trimmed);
                }
            }
        }
        spfContent.append(spfSoftFail ? " ~all" : " -all");

        records.add(DnsRecord.builder()
                .domain(domain)
                .type(DnsRecord.RecordType.TXT)
                .name("@")
                .content(spfContent.toString())
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.SPF)
                .build());

        // 3. DMARC Record
        StringBuilder dmarcContent = new StringBuilder("v=DMARC1;");
        dmarcContent.append(" p=").append(dmarcPolicy).append(";");
        if (!"none".equals(dmarcSubdomainPolicy)) {
             dmarcContent.append(" sp=").append(dmarcSubdomainPolicy).append(";");
        }
        if (dmarcPct < 100) {
             dmarcContent.append(" pct=").append(dmarcPct).append(";");
        }
        if (!"r".equals(dmarcAlignment)) { // Default is relaxed
             dmarcContent.append(" adkim=").append(dmarcAlignment).append(";");
             dmarcContent.append(" aspf=").append(dmarcAlignment).append(";");
        }
        if (reportingEmail != null && !reportingEmail.isEmpty()) {
             dmarcContent.append(" rua=mailto:").append(reportingEmail).append(";");
             dmarcContent.append(" ruf=mailto:").append(reportingEmail).append(";"); // Send forensic reports too
        }

        records.add(DnsRecord.builder()
                .domain(domain)
                .type(DnsRecord.RecordType.TXT)
                .name("_dmarc")
                .content(dmarcContent.toString())
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.DMARC)
                .build());

        // 4. DKIM Records (3 CNAMEs for rotation)
        String serverDomain = null;
        try {
            // Attempt to load server config without blocking indefinitely or crashing if missing
            Map<String, Object> serverConfig = configService.getConfig("server")
                    .onErrorResume(e -> Mono.empty())
                    .block();
            if (serverConfig != null) {
                serverDomain = (String) serverConfig.get("domainName");
            }
        } catch (Exception e) {
            log.warn("Failed to load server config for DKIM generation, using defaults", e);
        }

        // Fallback if server config missing or domainName not set
        if (serverDomain == null || serverDomain.isEmpty()) {
            serverDomain = domain.getDomain(); // Self-reference if no server domain configured
        }

        String prefix = domain.getDkimSelectorPrefix(); // Default "robin"
        
        if (domain.getId() == null) {
            // Transient domain (Discovery mode) - Return placeholders for DKIM
            for (int i = 1; i <= 3; i++) {
                String selector = prefix + i;
                records.add(DnsRecord.builder()
                        .domain(domain)
                        .type(domain.getDomain().equals(serverDomain) ? DnsRecord.RecordType.TXT : DnsRecord.RecordType.CNAME)
                        .name(selector + "._domainkey")
                        .content("PENDING_GENERATION")
                        .ttl(3600)
                        .purpose(DnsRecord.RecordPurpose.DKIM)
                        .build());
            }
        } else if (domain.getDomain().equals(serverDomain)) {
            // This IS the server domain (or we are falling back to self-hosting keys)
            // Generate/Ensure actual TXT records exist for the 3 selectors
            // Note: We are not generating keys here to avoid side effects in a getter-like method,
            // but we assume keys named prefix1, prefix2, prefix3 exist or will be created.
            // For now, we fetch existing keys. If this is a new setup, DkimService should probably 
            // ensure these exist for the server domain.
            
            // To properly support this, we check if keys exist, if not, we might create them?
            // Or better: Just check DB. If empty, maybe create?
            // Let's stick to reading DB. The creation should happen elsewhere or we iterate 1..3.
            
            // Actually, for the "Primary/System Domain", we want to expose the Public Keys.
            // We need 3 keys: prefix1, prefix2, prefix3.
            for (int i = 1; i <= 3; i++) {
                String selector = prefix + i;
                // Check if key exists in DB? 
                // Since this method is often called to preview records, we shouldn't modify DB.
                // But we can check DkimService.
                Optional<DkimKey> keyOpt = dkimService.getKeysForDomain(domain).stream()
                        .filter(k -> k.getSelector().equals(selector))
                        .findFirst();
                
                if (keyOpt.isPresent()) {
                    records.add(DnsRecord.builder()
                            .domain(domain)
                            .type(DnsRecord.RecordType.TXT)
                            .name(selector + "._domainkey")
                            .content("v=DKIM1; k=rsa; p=" + keyOpt.get().getPublicKey())
                            .ttl(3600)
                            .purpose(DnsRecord.RecordPurpose.DKIM)
                            .build());
                } else {
                    // Placeholder or indication that key needs generation
                    // For the system domain, we really should generate them.
                    // But avoiding side effects here.
                    // We'll return a placeholder or skip? 
                    // Let's create a "Pending Generation" record if possible, or trigger generation?
                    // To be safe: We generate them on the fly if missing (Transactional issue?)
                    // DnsRecordGenerator is likely called inside a Transaction in createDomain/syncDomain.
                    // So we can try to generate.
                    try {
                        DkimKey newKey = dkimService.generateKey(domain, selector);
                        if (i == 1) dkimService.activateKey(newKey.getId()); // Activate first one
                        records.add(DnsRecord.builder()
                                .domain(domain)
                                .type(DnsRecord.RecordType.TXT)
                                .name(selector + "._domainkey")
                                .content("v=DKIM1; k=rsa; p=" + newKey.getPublicKey())
                                .ttl(3600)
                                .purpose(DnsRecord.RecordPurpose.DKIM)
                                .build());
                    } catch (Exception e) {
                        log.error("Failed to generate auto-DKIM key for selector {}", selector, e);
                    }
                }
            }
        } else {
            // CNAME to Server Domain
            for (int i = 1; i <= 3; i++) {
                String selector = prefix + i;
                String target = selector + "._domainkey." + serverDomain;
                
                records.add(DnsRecord.builder()
                        .domain(domain)
                        .type(DnsRecord.RecordType.CNAME) // Ensure CNAME is in Enum
                        .name(selector + "._domainkey")
                        .content(target)
                        .ttl(3600)
                        .purpose(DnsRecord.RecordPurpose.DKIM)
                        .build());
            }
        }

        // 5. MTA-STS
        if (Boolean.TRUE.equals(domain.getMtaStsEnabled())) {
            records.add(DnsRecord.builder()
                    .domain(domain)
                    .type(DnsRecord.RecordType.TXT)
                    .name("_mta-sts")
                    .content("v=STSv1; id=" + System.currentTimeMillis())
                    .ttl(3600)
                    .purpose(DnsRecord.RecordPurpose.MTA_STS_RECORD)
                    .build());

            records.add(DnsRecord.builder()
                    .domain(domain)
                    .type(DnsRecord.RecordType.A)
                    .name("mta-sts")
                    .content(gatewayIp)
                    .ttl(3600)
                    .purpose(DnsRecord.RecordPurpose.MTA_STS_POLICY_HOST)
                    .build());
        }

        // 6. BIMI
        if (domain.getBimiSelector() != null && domain.getBimiLogoUrl() != null) {
            records.add(DnsRecord.builder()
                    .domain(domain)
                    .type(DnsRecord.RecordType.TXT)
                    .name(domain.getBimiSelector() + "._bimi")
                    .content("v=BIMI1; l=" + domain.getBimiLogoUrl() + ";")
                    .ttl(3600)
                    .purpose(DnsRecord.RecordPurpose.BIMI)
                    .build());
        }

        // 7. DANE (TLSA)
        if (Boolean.TRUE.equals(domain.getDaneEnabled())) {
            String certHash = certService.getCertificateHash(certPath);
            if (certHash != null) {
                records.add(DnsRecord.builder()
                        .domain(domain)
                        .type(DnsRecord.RecordType.TLSA)
                        .name("_25._tcp.mail")
                        .content("3 1 1 " + certHash)
                        .ttl(3600)
                        .purpose(DnsRecord.RecordPurpose.DANE)
                        .build());
            }
        }
        
        // 8. PTR Record (Recommended / Visual only)
        records.add(DnsRecord.builder()
                .domain(domain)
                .type(DnsRecord.RecordType.PTR)
                .name("PTR Record (Reverse DNS)")
                .content(gatewayIp + " -> mail." + domain.getDomain())
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.VERIFICATION)
                .syncStatus(DnsRecord.SyncStatus.PENDING)
                .build());

        // 9. Client Autoconfiguration (Thunderbird / Apple Mail)
        records.add(DnsRecord.builder()
                .domain(domain)
                .type(DnsRecord.RecordType.CNAME)
                .name("autoconfig")
                .content("mail." + domain.getDomain() + ".")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.SERVICE_DISCOVERY)
                .build());

        // 10. Client Autodiscover (Outlook)
        records.add(DnsRecord.builder()
                .domain(domain)
                .type(DnsRecord.RecordType.CNAME)
                .name("autodiscover")
                .content("mail." + domain.getDomain() + ".")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.SERVICE_DISCOVERY)
                .build());

        // 11. SRV Records for Service Discovery
        String[] srvRecords = {
            "_submission._tcp,587,mail",
            "_imaps._tcp,993,mail",
            "_pop3s._tcp,995,mail",
            "_autodiscover._tcp,443,mail",
            "_caldavs._tcp,443,mail",
            "_carddavs._tcp,443,mail"
        };

        for (String srv : srvRecords) {
            String[] parts = srv.split(",");
            records.add(DnsRecord.builder()
                    .domain(domain)
                    .type(DnsRecord.RecordType.SRV)
                    .name(parts[0])
                    .content("0 0 " + parts[1] + " " + parts[2] + "." + domain.getDomain() + ".")
                    .ttl(3600)
                    .purpose(DnsRecord.RecordPurpose.SERVICE_DISCOVERY)
                    .build());
        }

        return records;
    }
}