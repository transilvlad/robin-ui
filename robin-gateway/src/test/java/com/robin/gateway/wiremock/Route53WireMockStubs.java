package com.robin.gateway.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock stubs for the AWS Route53 API.
 * Covers: ListHostedZonesByName, ChangeResourceRecordSets, ListResourceRecordSets.
 *
 * Note: The AWS SDK for Route53 uses HTTPS against route53.amazonaws.com.
 * In tests the SDK endpoint is overridden to point at the WireMock server.
 */
public final class Route53WireMockStubs {

    public static final String HOSTED_ZONE_ID  = "Z1234567890ABC";
    public static final String CHANGE_ID       = "/change/C1234567890";

    private Route53WireMockStubs() {}

    /** Register all Route53 stubs against the given server. */
    public static void register(WireMockServer server) {
        registerListHostedZonesByName(server);
        registerChangeResourceRecordSets(server);
        registerListResourceRecordSets(server);
    }

    // ── Hosted Zones ──────────────────────────────────────────────────────────

    public static void registerListHostedZonesByName(WireMockServer server) {
        // GET /2013-04-01/hostedzonesbyname?dnsname=example.com → found
        server.stubFor(get(urlPathEqualTo("/2013-04-01/hostedzonesbyname"))
                .withQueryParam("dnsname", equalTo("example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("""
                                <?xml version="1.0" encoding="UTF-8"?>
                                <ListHostedZonesByNameResponse xmlns="https://route53.amazonaws.com/doc/2013-04-01/">
                                  <HostedZones>
                                    <HostedZone>
                                      <Id>/hostedzone/%s</Id>
                                      <Name>example.com.</Name>
                                    </HostedZone>
                                  </HostedZones>
                                  <IsTruncated>false</IsTruncated>
                                  <MaxItems>1</MaxItems>
                                </ListHostedZonesByNameResponse>
                                """.formatted(HOSTED_ZONE_ID))));

        // GET /2013-04-01/hostedzonesbyname?dnsname=unknown.com → empty
        server.stubFor(get(urlPathEqualTo("/2013-04-01/hostedzonesbyname"))
                .withQueryParam("dnsname", equalTo("unknown.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("""
                                <?xml version="1.0" encoding="UTF-8"?>
                                <ListHostedZonesByNameResponse xmlns="https://route53.amazonaws.com/doc/2013-04-01/">
                                  <HostedZones/>
                                  <IsTruncated>false</IsTruncated>
                                  <MaxItems>1</MaxItems>
                                </ListHostedZonesByNameResponse>
                                """)));
    }

    // ── Record Sets ───────────────────────────────────────────────────────────

    public static void registerChangeResourceRecordSets(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/2013-04-01/hostedzone/" + HOSTED_ZONE_ID + "/rrset/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("""
                                <?xml version="1.0" encoding="UTF-8"?>
                                <ChangeResourceRecordSetsResponse xmlns="https://route53.amazonaws.com/doc/2013-04-01/">
                                  <ChangeInfo>
                                    <Id>%s</Id>
                                    <Status>PENDING</Status>
                                    <SubmittedAt>2024-01-01T00:00:00Z</SubmittedAt>
                                  </ChangeInfo>
                                </ChangeResourceRecordSetsResponse>
                                """.formatted(CHANGE_ID))));
    }

    public static void registerListResourceRecordSets(WireMockServer server) {
        server.stubFor(get(urlEqualTo("/2013-04-01/hostedzone/" + HOSTED_ZONE_ID + "/rrset"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("""
                                <?xml version="1.0" encoding="UTF-8"?>
                                <ListResourceRecordSetsResponse xmlns="https://route53.amazonaws.com/doc/2013-04-01/">
                                  <ResourceRecordSets>
                                    <ResourceRecordSet>
                                      <Name>_dmarc.example.com.</Name>
                                      <Type>TXT</Type>
                                      <TTL>300</TTL>
                                      <ResourceRecords>
                                        <ResourceRecord>
                                          <Value>&quot;v=DMARC1; p=none&quot;</Value>
                                        </ResourceRecord>
                                      </ResourceRecords>
                                    </ResourceRecordSet>
                                  </ResourceRecordSets>
                                  <IsTruncated>false</IsTruncated>
                                  <MaxRRSets>300</MaxRRSets>
                                </ListResourceRecordSetsResponse>
                                """)));
    }
}
