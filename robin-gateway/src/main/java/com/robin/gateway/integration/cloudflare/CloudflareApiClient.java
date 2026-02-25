package com.robin.gateway.integration.cloudflare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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
                "content", content,
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
                });
    }

    public Mono<Void> updateDnsRecord(String zoneId, String recordId, String type, String name, String content, Integer ttl, String apiToken) {
        Map<String, Object> body = Map.of(
                "type", type,
                "name", name,
                "content", content,
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

    public Mono<String> createWorkerScript(String accountId, String scriptName, String jsCode, String apiToken) {
        return webClient.put()
                .uri("/accounts/{accountId}/workers/scripts/{scriptName}", accountId, scriptName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .contentType(MediaType.parseMediaType("application/javascript"))
                .bodyValue(jsCode)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.get("success").asBoolean()) {
                        return node.get("result").get("id").asText();
                    }
                    throw new RuntimeException("Failed to create worker script: " + node.get("errors").toString());
                });
    }

    public Mono<Void> createWorkerRoute(String zoneId, String pattern, String scriptName, String apiToken) {
        Map<String, Object> body = Map.of(
                "pattern", pattern,
                "script", scriptName
        );

        return webClient.post()
                .uri("/zones/{zoneId}/workers/routes", zoneId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(node -> {
                    if (node.get("success").asBoolean()) {
                        return Mono.empty();
                    }
                    return Mono.error(new RuntimeException("Failed to create worker route: " + node.get("errors").toString()));
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
                });
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
