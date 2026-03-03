package com.robin.gateway.integration.cloudflare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class CloudflareApiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CloudflareApiClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.cloudflare.com/client/v4")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
    }

    private WebClient.RequestHeadersSpec<?> createRequest(WebClient.RequestBodyUriSpec spec, String path, String token) {
        return spec.uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    
    private WebClient.RequestHeadersSpec<?> createGetRequest(String path, String token) {
        return webClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    
    private WebClient.RequestHeadersSpec<?> createDeleteRequest(String path, String token) {
        return webClient.delete().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    public Mono<String> getAccountId(String apiToken) {
        return createGetRequest("/accounts", apiToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.get("success").asBoolean()
                            && node.get("result").isArray()
                            && node.get("result").size() > 0) {
                        return node.get("result").get(0).get("id").asText();
                    }
                    throw new RuntimeException("No Cloudflare accounts found for the provided API token. "
                            + "Ensure the token has 'Account' read permissions.");
                });
    }

    public Mono<String> getZoneId(String domain, String apiToken) {
        return createGetRequest("/zones?name=" + domain, apiToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.get("success").asBoolean() && node.get("result").isArray() && node.get("result").size() > 0) {
                        return node.get("result").get(0).get("id").asText();
                    }
                    throw new RuntimeException("Zone not found for domain: " + domain);
                });
    }

    public Mono<String> createDnsRecord(String zoneId, String type, String name, String content, Integer ttl, String apiToken) {
        Map<String, Object> body = Map.of(
                "type", type,
                "name", name,
                "content", "TXT".equals(type) ? quoteTxtValue(content) : content,
                "ttl", ttl != null ? ttl : 1 // 1 is 'automatic' in Cloudflare
        );

        return webClient.post()
                .uri("/zones/{zoneId}/dns_records", zoneId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.get("success").asBoolean()) {
                        return node.get("result").get("id").asText();
                    }
                    throw new RuntimeException("Failed to create DNS record: " + node.get("errors").toString());
                })
                .onErrorResume(WebClientResponseException.BadRequest.class,
                        e -> findAndUpdateDnsRecord(zoneId, type, name, content, ttl, apiToken));
    }

    private Mono<String> findAndUpdateDnsRecord(String zoneId, String type, String name, String content, Integer ttl, String apiToken) {
        return createGetRequest("/zones/" + zoneId + "/dns_records?type=" + type + "&name=" + name, apiToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(node -> {
                    if (node.get("success").asBoolean() && node.get("result").size() > 0) {
                        String recordId = node.get("result").get(0).get("id").asText();
                        return updateDnsRecord(zoneId, recordId, type, name, content, ttl, apiToken)
                                .thenReturn(recordId);
                    }
                    throw new RuntimeException("DNS record '" + name + "' already exists but could not be found for update");
                });
    }

    public Mono<Void> updateDnsRecord(String zoneId, String recordId, String type, String name, String content, Integer ttl, String apiToken) {
        Map<String, Object> body = Map.of(
                "type", type,
                "name", name,
                "content", "TXT".equals(type) ? quoteTxtValue(content) : content,
                "ttl", ttl != null ? ttl : 1
        );

        return webClient.put()
                .uri("/zones/{zoneId}/dns_records/{recordId}", zoneId, recordId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(node -> {
                    if (node.get("success").asBoolean()) {
                        return Mono.empty();
                    }
                    return Mono.error(new RuntimeException("Failed to update DNS record: " + node.get("errors").toString()));
                });
    }

    public Mono<Void> deleteDnsRecord(String zoneId, String recordId, String apiToken) {
        return webClient.delete()
                .uri("/zones/{zoneId}/dns_records/{recordId}", zoneId, recordId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(node -> {
                    if (node.get("success").asBoolean()) {
                        return Mono.empty();
                    }
                    return Mono.error(new RuntimeException("Failed to delete DNS record: " + node.get("errors").toString()));
                });
    }
    
    public Mono<JsonNode> listDnsRecords(String zoneId, String apiToken) {
        return webClient.get()
                .uri("/zones/{zoneId}/dns_records", zoneId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.get("success").asBoolean()) {
                        return node.get("result");
                    }
                    throw new RuntimeException("Failed to list DNS records: " + node.get("errors").toString());
                });
    }

    public Mono<String> createWorkerScript(String accountId, String scriptName, String jsCode, String kvNamespaceId, String apiToken) {
        // ES module workers require multipart/form-data upload with metadata declaring bindings.
        // Uploading with application/javascript causes Cloudflare to reject the ES module syntax.
        String metadata = String.format(
                "{\"main_module\":\"worker.js\",\"bindings\":[{\"type\":\"kv_namespace\",\"name\":\"POLICY_KV\",\"namespace_id\":\"%s\"}]}",
                kvNamespaceId
        );
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("metadata", metadata, MediaType.APPLICATION_JSON);
        builder.part("worker.js", jsCode, MediaType.parseMediaType("application/javascript+module"))
                .filename("worker.js");

        return webClient.put()
                .uri("/accounts/{accountId}/workers/scripts/{scriptName}", accountId, scriptName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.get("success").asBoolean()) {
                        return node.get("result").get("id").asText();
                    }
                    throw new RuntimeException("Failed to create worker script: " + node.get("errors").toString());
                });
    }

    public Mono<String> getWorkerScriptId(String accountId, String scriptName, String apiToken) {
        return createGetRequest("/accounts/" + accountId + "/workers/scripts/" + scriptName, apiToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(node -> {
                    if (!node.get("success").asBoolean()) {
                        return Mono.error(new RuntimeException("Failed to get worker script: " + node.get("errors")));
                    }

                    JsonNode result = node.get("result");
                    if (result == null || result.isNull()) {
                        return Mono.just(scriptName);
                    }

                    JsonNode idNode = result.get("id");
                    if (idNode != null && !idNode.isNull() && !idNode.asText().isBlank()) {
                        return Mono.just(idNode.asText());
                    }

                    return Mono.just(scriptName);
                })
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }

    public Mono<Void> addWorkerCustomDomain(String accountId, String hostname, String zoneId, String workerName, String apiToken) {
        Map<String, Object> body = Map.of(
                "hostname", hostname,
                "zone_id", zoneId,
                "service", workerName,
                "environment", "production"
        );

        return webClient.put()
                .uri("/accounts/{accountId}/workers/domains", accountId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(node -> {
                    if (node.get("success").asBoolean()) {
                        return Mono.<Void>empty();
                    }
                    return Mono.<Void>error(new RuntimeException("Failed to add custom domain to worker: " + node.get("errors").toString()));
                })
                .onErrorResume(WebClientResponseException.Conflict.class, e -> {
                    log.info("Custom domain already attached to worker, continuing");
                    return Mono.<Void>empty();
                });
    }

    public Mono<String> createKvNamespace(String accountId, String title, String apiToken) {
        Map<String, Object> body = Map.of("title", title);

        return webClient.post()
                .uri("/accounts/{accountId}/storage/kv/namespaces", accountId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.get("success").asBoolean()) {
                        return node.get("result").get("id").asText();
                    }
                    throw new RuntimeException("Failed to create KV namespace: " + node.get("errors").toString());
                })
                .onErrorResume(WebClientResponseException.BadRequest.class,
                        e -> findKvNamespaceByTitle(accountId, title, apiToken));
    }

    private Mono<String> findKvNamespaceByTitle(String accountId, String title, String apiToken) {
        return createGetRequest("/accounts/" + accountId + "/storage/kv/namespaces", apiToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.get("success").asBoolean()) {
                        for (JsonNode ns : node.get("result")) {
                            if (title.equals(ns.get("title").asText())) {
                                return ns.get("id").asText();
                            }
                        }
                    }
                    throw new RuntimeException("KV namespace '" + title + "' already exists but could not be found in account");
                });
    }

    /** Wraps a TXT record value in double quotes if not already quoted. */
    private String quoteTxtValue(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed;
        }
        return "\"" + trimmed + "\"";
    }

    public Mono<Void> updateWorkerKv(String accountId, String namespaceId, String key, String value, String apiToken) {
        return webClient.put()
                .uri("/accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{key}", accountId, namespaceId, key)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(value)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(node -> {
                    if (node.get("success").asBoolean()) {
                        return Mono.empty();
                    }
                    return Mono.error(new RuntimeException("Failed to update KV store: " + node.get("errors").toString()));
                });
    }
}
