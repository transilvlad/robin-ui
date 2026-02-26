package com.robin.gateway.model.dto;

import com.robin.gateway.model.DnsProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of a DNS pre-flight lookup performed before adding a domain.
 * Contains existing DNS record values and a suggested DNS/NS provider
 * based on the detected nameserver vendor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainLookupResult {

    /** The domain that was queried. */
    private String domain;

    /** Authoritative nameserver hostnames (NS records). */
    private List<String> nsRecords;

    /** MX records in the form "priority target" (e.g. "10 mail.example.com"). */
    private List<String> mxRecords;

    /** Values of TXT records at the apex (@). */
    private List<String> spfRecords;

    /** Values of TXT records at _dmarc.<domain>. */
    private List<String> dmarcRecords;

    /** Values of TXT records at _mta-sts.<domain>. */
    private List<String> mtaStsRecords;

    /** Values of TXT records at _smtp._tls.<domain>. */
    private List<String> smtpTlsRecords;

    /**
     * Vendor detected from NS patterns.
     * One of: CLOUDFLARE, AWS_ROUTE53, UNKNOWN.
     */
    private String detectedNsProviderType;

    /**
     * A registered DnsProvider whose type matches the detected NS vendor.
     * Null when no match is found or vendor is UNKNOWN.
     */
    private DnsProvider suggestedProvider;

    /** All registered DNS providers – used to populate provider dropdowns. */
    private List<DnsProvider> availableProviders;
}
