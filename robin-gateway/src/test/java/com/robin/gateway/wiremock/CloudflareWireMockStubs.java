package com.robin.gateway.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock stubs for the Cloudflare API.
 * Covers: zones lookup, DNS record CRUD, Workers script/route, KV namespace/values.
 */
public final class CloudflareWireMockStubs {

    public static final String ZONE_ID      = "abc123zone";
    public static final String ACCOUNT_ID   = "acc456";
    public static final String DNS_RECORD_ID = "rec789";
    public static final String WORKER_ID     = "wkr001";
    public static final String KV_NS_ID      = "kv001";

    private CloudflareWireMockStubs() {}

    /** Register all Cloudflare stubs against the given server. */
    public static void register(WireMockServer server) {
        registerZoneStubs(server);
        registerDnsStubs(server);
        registerWorkerStubs(server);
        registerKvStubs(server);
    }

    // ── Zones ──────────────────────────────────────────────────────────────────

    public static void registerZoneStubs(WireMockServer server) {
        // GET /zones?name=example.com → found
        server.stubFor(get(urlPathEqualTo("/client/v4/zones"))
                .withQueryParam("name", equalTo("example.com"))
                .willReturn(okJson("""
                        {"success":true,"result":[{"id":"%s","name":"example.com"}]}
                        """.formatted(ZONE_ID))));

        // GET /zones?name=unknown.com → not found
        server.stubFor(get(urlPathEqualTo("/client/v4/zones"))
                .withQueryParam("name", equalTo("unknown.com"))
                .willReturn(okJson("""
                        {"success":true,"result":[]}
                        """)));
    }

    // ── DNS Records ───────────────────────────────────────────────────────────

    public static void registerDnsStubs(WireMockServer server) {
        // POST /zones/{zoneId}/dns_records → created
        server.stubFor(post(urlEqualTo("/client/v4/zones/" + ZONE_ID + "/dns_records"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success":true,"result":{"id":"%s","type":"TXT","name":"_dmarc.example.com","content":"v=DMARC1"}}
                                """.formatted(DNS_RECORD_ID))));

        // PUT /zones/{zoneId}/dns_records/{recordId} → updated
        server.stubFor(put(urlEqualTo("/client/v4/zones/" + ZONE_ID + "/dns_records/" + DNS_RECORD_ID))
                .willReturn(okJson("""
                        {"success":true,"result":{"id":"%s"}}
                        """.formatted(DNS_RECORD_ID))));

        // DELETE /zones/{zoneId}/dns_records/{recordId} → deleted
        server.stubFor(delete(urlEqualTo("/client/v4/zones/" + ZONE_ID + "/dns_records/" + DNS_RECORD_ID))
                .willReturn(okJson("""
                        {"success":true,"result":{"id":"%s"}}
                        """.formatted(DNS_RECORD_ID))));

        // GET /zones/{zoneId}/dns_records → list
        server.stubFor(get(urlEqualTo("/client/v4/zones/" + ZONE_ID + "/dns_records"))
                .willReturn(okJson("""
                        {"success":true,"result":[{"id":"%s","type":"TXT","name":"_dmarc.example.com","content":"v=DMARC1"}]}
                        """.formatted(DNS_RECORD_ID))));
    }

    // ── Workers ───────────────────────────────────────────────────────────────

    public static void registerWorkerStubs(WireMockServer server) {
        // PUT /accounts/{accountId}/workers/scripts/{scriptName}
        server.stubFor(put(urlMatching("/client/v4/accounts/" + ACCOUNT_ID + "/workers/scripts/.*"))
                .willReturn(okJson("""
                        {"success":true,"result":{"id":"%s"}}
                        """.formatted(WORKER_ID))));

        // POST /zones/{zoneId}/workers/routes
        server.stubFor(post(urlEqualTo("/client/v4/zones/" + ZONE_ID + "/workers/routes"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success":true,"result":{"id":"route001"}}
                                """)));
    }

    // ── KV ────────────────────────────────────────────────────────────────────

    public static void registerKvStubs(WireMockServer server) {
        // POST /accounts/{accountId}/storage/kv/namespaces
        server.stubFor(post(urlEqualTo("/client/v4/accounts/" + ACCOUNT_ID + "/storage/kv/namespaces"))
                .willReturn(okJson("""
                        {"success":true,"result":{"id":"%s"}}
                        """.formatted(KV_NS_ID))));

        // PUT /accounts/{accountId}/storage/kv/namespaces/{nsId}/values/{key}
        server.stubFor(put(urlMatching(
                "/client/v4/accounts/" + ACCOUNT_ID + "/storage/kv/namespaces/" + KV_NS_ID + "/values/.*"))
                .willReturn(okJson("""
                        {"success":true,"result":null}
                        """)));
    }
}
