package com.robin.gateway.service;

import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DnsRecordGenerator {

    private final DkimService dkimService;
    private final CertService certService;

    @Value("${robin.gateway.external-ip:127.0.0.1}")
    private String gatewayIp;

    @Value("${robin.mail.cert-path:/etc/ssl/certs/mail.pem}")
    private String certPath;

    public List<DnsRecord> generateExpectedRecords(Domain domain) {
        List<DnsRecord> records = new ArrayList<>();
        
        // ...
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
        records.add(DnsRecord.builder()
                .domain(domain)
                .type(DnsRecord.RecordType.TXT)
                .name("@")
                .content("v=spf1 mx ~all")
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.SPF)
                .build());

        // 3. DMARC Record
        records.add(DnsRecord.builder()
                .domain(domain)
                .type(DnsRecord.RecordType.TXT)
                .name("_dmarc")
                .content("v=DMARC1; p=none; rua=mailto:postmaster@" + domain.getDomain())
                .ttl(3600)
                .purpose(DnsRecord.RecordPurpose.DMARC)
                .build());

        // 4. DKIM Records
        List<DkimKey> keys = dkimService.getKeysForDomain(domain);
        for (DkimKey key : keys) {
            records.add(DnsRecord.builder()
                    .domain(domain)
                    .type(DnsRecord.RecordType.TXT)
                    .name(key.getSelector() + "._domainkey")
                    .content("v=DKIM1; k=rsa; p=" + key.getPublicKey())
                    .ttl(3600)
                    .purpose(DnsRecord.RecordPurpose.DKIM)
                    .build());
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

        return records;
    }
}