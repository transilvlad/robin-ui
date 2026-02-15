package com.robin.gateway.service;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.DkimKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DnsRecordGenerator Tests")
class DnsRecordGeneratorTest {

    @Mock
    private DkimService dkimService;

    @Mock
    private CertService certService;

    @Mock
    private ConfigurationService configService;

    private DnsRecordGenerator dnsRecordGenerator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dnsRecordGenerator = new DnsRecordGenerator(dkimService, certService, configService);

        // Set default values
        ReflectionTestUtils.setField(dnsRecordGenerator, "gatewayIp", "1.2.3.4");
        ReflectionTestUtils.setField(dnsRecordGenerator, "certPath", "/etc/ssl/certs/mail.pem");

        // Default: config service returns empty/default config
        when(configService.getConfig("email_reporting")).thenReturn(Mono.empty());
        when(configService.getConfig("server")).thenReturn(Mono.empty());

        // Default: dkim service returns valid key
        DkimKey dummyKey = DkimKey.builder()
                .id(1L)
                .selector("robin1")
                .publicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...")
                .build();
        when(dkimService.generateKey(any(Domain.class), anyString())).thenReturn(dummyKey);
    }

    @Test
    @DisplayName("Should generate basic email records with defaults")
    void testGenerateBasicRecords() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        assertThat(records).isNotNull();
        assertThat(records).hasSizeGreaterThan(3);

        // Should have A record for mail host
        assertThat(records).anyMatch(r ->
                r.getType() == DnsRecord.RecordType.A &&
                        "mail".equals(r.getName()) &&
                        "1.2.3.4".equals(r.getContent()));

        // Should have MX record
        assertThat(records).anyMatch(r ->
                r.getType() == DnsRecord.RecordType.MX &&
                        "@".equals(r.getName()) &&
                        r.getContent().contains("mail.test.com"));

        // Should have SPF record
        assertThat(records).anyMatch(r ->
                r.getType() == DnsRecord.RecordType.TXT &&
                        r.getPurpose() == DnsRecord.RecordPurpose.SPF &&
                        r.getContent().startsWith("v=spf1"));

        // Should have DMARC record
        assertThat(records).anyMatch(r ->
                r.getType() == DnsRecord.RecordType.TXT &&
                        "_dmarc".equals(r.getName()) &&
                        r.getContent().startsWith("v=DMARC1"));
    }

    @Test
    @DisplayName("Should use global configuration defaults")
    void testGenerateWithGlobalConfig() {
        Map<String, Object> emailReportingConfig = new HashMap<>();
        emailReportingConfig.put("reportingEmail", "dmarc-reports@test.com");

        Map<String, Object> dmarcConfig = new HashMap<>();
        dmarcConfig.put("policy", "quarantine");
        dmarcConfig.put("subdomainPolicy", "reject");
        dmarcConfig.put("percentage", 100);
        dmarcConfig.put("alignment", "s");
        emailReportingConfig.put("dmarc", dmarcConfig);

        Map<String, Object> spfConfig = new HashMap<>();
        spfConfig.put("includes", "_spf.google.com");
        spfConfig.put("softFail", false);
        emailReportingConfig.put("spf", spfConfig);

        when(configService.getConfig("email_reporting")).thenReturn(Mono.just(emailReportingConfig));

        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        // Check DMARC uses global config
        DnsRecord dmarcRecord = records.stream()
                .filter(r -> "_dmarc".equals(r.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(dmarcRecord.getContent()).contains("p=quarantine");
        assertThat(dmarcRecord.getContent()).contains("sp=reject");
        assertThat(dmarcRecord.getContent()).contains("adkim=s");
        assertThat(dmarcRecord.getContent()).contains("rua=mailto:dmarc-reports@test.com");

        // Check SPF uses global config
        DnsRecord spfRecord = records.stream()
                .filter(r -> r.getPurpose() == DnsRecord.RecordPurpose.SPF)
                .findFirst()
                .orElseThrow();

        assertThat(spfRecord.getContent()).contains("include:_spf.google.com");
        assertThat(spfRecord.getContent()).endsWith("-all"); // Hard fail
    }

    @Test
    @DisplayName("Should override global config with domain-specific settings")
    void testDomainConfigOverridesGlobal() {
        Map<String, Object> emailReportingConfig = new HashMap<>();
        emailReportingConfig.put("reportingEmail", "global@test.com");

        Map<String, Object> dmarcConfig = new HashMap<>();
        dmarcConfig.put("policy", "none");
        emailReportingConfig.put("dmarc", dmarcConfig);

        when(configService.getConfig("email_reporting")).thenReturn(Mono.just(emailReportingConfig));

        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .dmarcPolicy("quarantine") // Domain override
                .dmarcReportingEmail("domain-specific@test.com") // Domain override
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord dmarcRecord = records.stream()
                .filter(r -> "_dmarc".equals(r.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(dmarcRecord.getContent()).contains("p=quarantine");
        assertThat(dmarcRecord.getContent()).contains("rua=mailto:domain-specific@test.com");
    }

    @Test
    @DisplayName("Should generate SPF with multiple includes")
    void testSpfMultipleIncludes() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .spfIncludes("_spf.google.com,_spf.sendgrid.net,spf.protection.outlook.com")
                .spfSoftFail(true)
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord spfRecord = records.stream()
                .filter(r -> r.getPurpose() == DnsRecord.RecordPurpose.SPF)
                .findFirst()
                .orElseThrow();

        assertThat(spfRecord.getContent()).contains("include:_spf.google.com");
        assertThat(spfRecord.getContent()).contains("include:_spf.sendgrid.net");
        assertThat(spfRecord.getContent()).contains("include:spf.protection.outlook.com");
        assertThat(spfRecord.getContent()).endsWith("~all");
    }

    @Test
    @DisplayName("Should generate SPF with gateway IP")
    void testSpfWithGatewayIp() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord spfRecord = records.stream()
                .filter(r -> r.getPurpose() == DnsRecord.RecordPurpose.SPF)
                .findFirst()
                .orElseThrow();

        assertThat(spfRecord.getContent()).contains("ip4:1.2.3.4");
        assertThat(spfRecord.getContent()).startsWith("v=spf1");
        assertThat(spfRecord.getContent()).contains("mx");
    }

    @Test
    @DisplayName("Should not include localhost IP in SPF")
    void testSpfExcludesLocalhost() {
        ReflectionTestUtils.setField(dnsRecordGenerator, "gatewayIp", "127.0.0.1");

        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord spfRecord = records.stream()
                .filter(r -> r.getPurpose() == DnsRecord.RecordPurpose.SPF)
                .findFirst()
                .orElseThrow();

        assertThat(spfRecord.getContent()).doesNotContain("ip4:127.0.0.1");
    }

    @Test
    @DisplayName("Should generate DMARC with minimal policy")
    void testDmarcMinimal() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .dmarcPolicy("none")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord dmarcRecord = records.stream()
                .filter(r -> "_dmarc".equals(r.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(dmarcRecord.getContent()).startsWith("v=DMARC1;");
        assertThat(dmarcRecord.getContent()).contains("p=none");
        assertThat(dmarcRecord.getContent()).contains("rua=mailto:");
    }

    @Test
    @DisplayName("Should generate DMARC with subdomain policy")
    void testDmarcWithSubdomainPolicy() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .dmarcPolicy("quarantine")
                .dmarcSubdomainPolicy("reject")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord dmarcRecord = records.stream()
                .filter(r -> "_dmarc".equals(r.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(dmarcRecord.getContent()).contains("p=quarantine");
        assertThat(dmarcRecord.getContent()).contains("sp=reject");
    }

    @Test
    @DisplayName("Should not include default DMARC values")
    void testDmarcOmitsDefaults() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .dmarcPolicy("quarantine")
                .dmarcSubdomainPolicy("none") // Same as default
                .dmarcPercentage(100) // Default
                .dmarcAlignment("r") // Default
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord dmarcRecord = records.stream()
                .filter(r -> "_dmarc".equals(r.getName()))
                .findFirst()
                .orElseThrow();

        // Should not include sp (same as default "none")
        // Should not include pct (100 is default)
        // Should not include adkim (r is default)
        assertThat(dmarcRecord.getContent()).doesNotContain("sp=none");
        assertThat(dmarcRecord.getContent()).doesNotContain("pct=100");
        assertThat(dmarcRecord.getContent()).doesNotContain("adkim=r");
    }

    @Test
    @DisplayName("Should include DMARC percentage when less than 100")
    void testDmarcPartialPercentage() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .dmarcPolicy("quarantine")
                .dmarcPercentage(50)
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord dmarcRecord = records.stream()
                .filter(r -> "_dmarc".equals(r.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(dmarcRecord.getContent()).contains("pct=50");
    }

    @Test
    @DisplayName("Should include strict alignment when specified")
    void testDmarcStrictAlignment() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .dmarcPolicy("reject")
                .dmarcAlignment("s")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord dmarcRecord = records.stream()
                .filter(r -> "_dmarc".equals(r.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(dmarcRecord.getContent()).contains("adkim=s");
        assertThat(dmarcRecord.getContent()).contains("aspf=s");
    }

    @Test
    @DisplayName("Should set correct TTL for all records")
    void testRecordsTtl() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        // All generated records should have TTL of 3600
        assertThat(records)
                .allMatch(r -> r.getTtl() != null && r.getTtl() == 3600);
    }

    @Test
    @DisplayName("Should handle config service failure gracefully")
    void testConfigServiceFailure() {
        when(configService.getConfig("email_reporting"))
                .thenReturn(Mono.error(new RuntimeException("Config error")));

        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .build();

        // Should not throw, should use defaults
        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        assertThat(records).isNotEmpty();
        assertThat(records).anyMatch(r -> r.getPurpose() == DnsRecord.RecordPurpose.SPF);
        assertThat(records).anyMatch(r -> r.getPurpose() == DnsRecord.RecordPurpose.DMARC);
    }

    @Test
    @DisplayName("Should use default reporting email when not configured")
    void testDefaultReportingEmail() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord dmarcRecord = records.stream()
                .filter(r -> "_dmarc".equals(r.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(dmarcRecord.getContent()).contains("rua=mailto:postmaster@test.com");
    }

    @Test
    @DisplayName("Should handle empty SPF includes")
    void testEmptySpfIncludes() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .spfIncludes("")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord spfRecord = records.stream()
                .filter(r -> r.getPurpose() == DnsRecord.RecordPurpose.SPF)
                .findFirst()
                .orElseThrow();

        // Should still have basic SPF structure
        assertThat(spfRecord.getContent()).startsWith("v=spf1");
        assertThat(spfRecord.getContent()).contains("mx");
        assertThat(spfRecord.getContent()).endsWith("~all");
    }

    @Test
    @DisplayName("Should handle SPF includes with include: prefix")
    void testSpfIncludesWithPrefix() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .spfIncludes("include:_spf.google.com,_spf.sendgrid.net")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord spfRecord = records.stream()
                .filter(r -> r.getPurpose() == DnsRecord.RecordPurpose.SPF)
                .findFirst()
                .orElseThrow();

        // Should handle mixed formats
        assertThat(spfRecord.getContent()).contains("include:_spf.google.com");
        assertThat(spfRecord.getContent()).contains("include:_spf.sendgrid.net");
        // Should not double-prefix
        assertThat(spfRecord.getContent()).doesNotContain("include:include:");
    }

    @Test
    @DisplayName("Should generate MX record with correct priority")
    void testMxRecordPriority() {
        Domain domain = Domain.builder()
                .id(1L)
                .domain("test.com")
                .build();

        List<DnsRecord> records = dnsRecordGenerator.generateExpectedRecords(domain);

        DnsRecord mxRecord = records.stream()
                .filter(r -> r.getType() == DnsRecord.RecordType.MX)
                .findFirst()
                .orElseThrow();

        assertThat(mxRecord.getPriority()).isEqualTo(10);
        assertThat(mxRecord.getName()).isEqualTo("@");
        assertThat(mxRecord.getContent()).endsWith("."); // FQDN with trailing dot
    }
}
