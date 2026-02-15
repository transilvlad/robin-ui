package com.robin.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.controller.ProviderController.CreateProviderRequest;
import com.robin.gateway.model.ProviderConfig;
import com.robin.gateway.service.EncryptionService;
import com.robin.gateway.service.ProviderConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ProviderController Tests")
class ProviderControllerTest {

    @Mock
    private ProviderConfigService providerConfigService;

    @Mock
    private EncryptionService encryptionService;

    private ObjectMapper objectMapper;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();

        ProviderController controller = new ProviderController(
            providerConfigService,
            encryptionService,
            objectMapper
        );

        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/providers should return paginated providers with default pagination")
    void testGetAllProvidersDefaultPagination() throws Exception {
        // Given
        ProviderConfig provider1 = ProviderConfig.builder()
            .id(1L)
            .name("Cloudflare DNS")
            .type(ProviderConfig.ProviderType.CLOUDFLARE)
            .credentials("encrypted_credentials_1")
            .build();

        ProviderConfig provider2 = ProviderConfig.builder()
            .id(2L)
            .name("AWS Route53")
            .type(ProviderConfig.ProviderType.AWS_ROUTE53)
            .credentials("encrypted_credentials_2")
            .build();

        List<ProviderConfig> providers = List.of(provider1, provider2);
        Page<ProviderConfig> page = new PageImpl<>(providers, PageRequest.of(0, 10), 2);

        // Mock encryption service to return decrypted credentials
        Map<String, String> creds1 = Map.of("apiToken", "secret123", "email", "user@test.com");
        when(encryptionService.decrypt("encrypted_credentials_1"))
            .thenReturn(objectMapper.writeValueAsString(creds1));

        Map<String, String> creds2 = Map.of("accessKeyId", "AKIATEST", "secretAccessKey", "secret456");
        when(encryptionService.decrypt("encrypted_credentials_2"))
            .thenReturn(objectMapper.writeValueAsString(creds2));

        when(providerConfigService.getAllProviders(any(Pageable.class)))
            .thenReturn(Mono.just(page));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/providers")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].id").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("Cloudflare DNS")
            .jsonPath("$.content[0].type").isEqualTo("CLOUDFLARE")
            .jsonPath("$.content[0].credentials.apiToken").isEqualTo("********")
            .jsonPath("$.content[0].credentials.email").isEqualTo("user@test.com")
            .jsonPath("$.content[1].credentials.accessKeyId").isEqualTo("********")
            .jsonPath("$.content[1].credentials.secretAccessKey").isEqualTo("********")
            .jsonPath("$.totalElements").isEqualTo(2);

        verify(providerConfigService).getAllProviders(argThat(pageable ->
            pageable.getPageNumber() == 0 && pageable.getPageSize() == 10
        ));
    }

    @Test
    @DisplayName("GET /api/v1/providers should return paginated providers with custom pagination")
    void testGetAllProvidersCustomPagination() {
        // Given
        Page<ProviderConfig> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(1, 5), 0);

        when(providerConfigService.getAllProviders(any(Pageable.class)))
            .thenReturn(Mono.just(page));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/providers?page=1&size=5")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.size").isEqualTo(5)
            .jsonPath("$.number").isEqualTo(1);

        verify(providerConfigService).getAllProviders(argThat(pageable ->
            pageable.getPageNumber() == 1 && pageable.getPageSize() == 5
        ));
    }

    @Test
    @DisplayName("GET /api/v1/providers should handle empty provider list")
    void testGetAllProvidersEmpty() {
        // Given
        Page<ProviderConfig> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 10), 0);

        when(providerConfigService.getAllProviders(any(Pageable.class)))
            .thenReturn(Mono.just(page));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/providers")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content.length()").isEqualTo(0);

        verify(providerConfigService).getAllProviders(any(Pageable.class));
    }

    @Test
    @DisplayName("POST /api/v1/providers should create Cloudflare provider with sanitized response")
    void testCreateCloudflareProvider() throws Exception {
        // Given
        Map<String, String> credentials = new HashMap<>();
        credentials.put("apiToken", "cf_token_12345");
        credentials.put("email", "admin@test.com");

        CreateProviderRequest request = new CreateProviderRequest();
        request.setName("Cloudflare DNS");
        request.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        request.setCredentials(credentials);

        ProviderConfig createdProvider = ProviderConfig.builder()
            .id(10L)
            .name("Cloudflare DNS")
            .type(ProviderConfig.ProviderType.CLOUDFLARE)
            .credentials("encrypted_data")
            .build();

        when(providerConfigService.createProvider(
            eq("Cloudflare DNS"),
            eq(ProviderConfig.ProviderType.CLOUDFLARE),
            eq(credentials)
        )).thenReturn(Mono.just(createdProvider));

        when(encryptionService.decrypt("encrypted_data"))
            .thenReturn(objectMapper.writeValueAsString(credentials));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/providers")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(10)
            .jsonPath("$.name").isEqualTo("Cloudflare DNS")
            .jsonPath("$.type").isEqualTo("CLOUDFLARE")
            .jsonPath("$.credentials.apiToken").isEqualTo("********")
            .jsonPath("$.credentials.email").isEqualTo("admin@test.com");

        verify(providerConfigService).createProvider(
            eq("Cloudflare DNS"),
            eq(ProviderConfig.ProviderType.CLOUDFLARE),
            eq(credentials)
        );
    }

    @Test
    @DisplayName("POST /api/v1/providers should create AWS Route53 provider")
    void testCreateAwsRoute53Provider() throws Exception {
        // Given
        Map<String, String> credentials = new HashMap<>();
        credentials.put("accessKeyId", "AKIAIOSFODNN7EXAMPLE");
        credentials.put("secretAccessKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        credentials.put("region", "us-east-1");

        CreateProviderRequest request = new CreateProviderRequest();
        request.setName("AWS Route53");
        request.setType(ProviderConfig.ProviderType.AWS_ROUTE53);
        request.setCredentials(credentials);

        ProviderConfig createdProvider = ProviderConfig.builder()
            .id(20L)
            .name("AWS Route53")
            .type(ProviderConfig.ProviderType.AWS_ROUTE53)
            .credentials("encrypted_data")
            .build();

        when(providerConfigService.createProvider(
            eq("AWS Route53"),
            eq(ProviderConfig.ProviderType.AWS_ROUTE53),
            eq(credentials)
        )).thenReturn(Mono.just(createdProvider));

        when(encryptionService.decrypt("encrypted_data"))
            .thenReturn(objectMapper.writeValueAsString(credentials));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/providers")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(20)
            .jsonPath("$.name").isEqualTo("AWS Route53")
            .jsonPath("$.type").isEqualTo("AWS_ROUTE53")
            .jsonPath("$.credentials.accessKeyId").isEqualTo("********")
            .jsonPath("$.credentials.secretAccessKey").isEqualTo("********")
            .jsonPath("$.credentials.region").isEqualTo("us-east-1");

        verify(providerConfigService).createProvider(
            eq("AWS Route53"),
            eq(ProviderConfig.ProviderType.AWS_ROUTE53),
            eq(credentials)
        );
    }

    @Test
    @DisplayName("POST /api/v1/providers should create GoDaddy provider")
    void testCreateGoDaddyProvider() throws Exception {
        // Given
        Map<String, String> credentials = new HashMap<>();
        credentials.put("apiKey", "godaddy_key_12345");
        credentials.put("apiSecret", "godaddy_secret_67890");

        CreateProviderRequest request = new CreateProviderRequest();
        request.setName("GoDaddy DNS");
        request.setType(ProviderConfig.ProviderType.GODADDY);
        request.setCredentials(credentials);

        ProviderConfig createdProvider = ProviderConfig.builder()
            .id(30L)
            .name("GoDaddy DNS")
            .type(ProviderConfig.ProviderType.GODADDY)
            .credentials("encrypted_data")
            .build();

        when(providerConfigService.createProvider(
            eq("GoDaddy DNS"),
            eq(ProviderConfig.ProviderType.GODADDY),
            eq(credentials)
        )).thenReturn(Mono.just(createdProvider));

        when(encryptionService.decrypt("encrypted_data"))
            .thenReturn(objectMapper.writeValueAsString(credentials));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/providers")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(30)
            .jsonPath("$.name").isEqualTo("GoDaddy DNS")
            .jsonPath("$.type").isEqualTo("GODADDY")
            .jsonPath("$.credentials.apiKey").isEqualTo("********")
            .jsonPath("$.credentials.apiSecret").isEqualTo("********");

        verify(providerConfigService).createProvider(
            eq("GoDaddy DNS"),
            eq(ProviderConfig.ProviderType.GODADDY),
            eq(credentials)
        );
    }

    @Test
    @DisplayName("POST /api/v1/providers should handle service error")
    void testCreateProviderServiceError() {
        // Given
        Map<String, String> credentials = new HashMap<>();
        credentials.put("apiToken", "token");

        CreateProviderRequest request = new CreateProviderRequest();
        request.setName("Duplicate Provider");
        request.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        request.setCredentials(credentials);

        when(providerConfigService.createProvider(anyString(), any(), anyMap()))
            .thenReturn(Mono.error(new IllegalArgumentException("Provider already exists")));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/providers")
            .bodyValue(request)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(providerConfigService).createProvider(anyString(), any(), anyMap());
    }

    @Test
    @DisplayName("PUT /api/v1/providers/{id} should update provider with sanitized response")
    void testUpdateProvider() throws Exception {
        // Given
        Long providerId = 1L;

        Map<String, String> credentials = new HashMap<>();
        credentials.put("apiToken", "new_token_12345");
        credentials.put("email", "updated@test.com");

        CreateProviderRequest request = new CreateProviderRequest();
        request.setName("Updated Cloudflare");
        request.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        request.setCredentials(credentials);

        ProviderConfig updatedProvider = ProviderConfig.builder()
            .id(providerId)
            .name("Updated Cloudflare")
            .type(ProviderConfig.ProviderType.CLOUDFLARE)
            .credentials("encrypted_data")
            .build();

        when(providerConfigService.updateProvider(
            eq(providerId),
            eq("Updated Cloudflare"),
            eq(ProviderConfig.ProviderType.CLOUDFLARE),
            eq(credentials)
        )).thenReturn(Mono.just(updatedProvider));

        when(encryptionService.decrypt("encrypted_data"))
            .thenReturn(objectMapper.writeValueAsString(credentials));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/providers/{id}", providerId)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(providerId)
            .jsonPath("$.name").isEqualTo("Updated Cloudflare")
            .jsonPath("$.credentials.apiToken").isEqualTo("********")
            .jsonPath("$.credentials.email").isEqualTo("updated@test.com");

        verify(providerConfigService).updateProvider(
            eq(providerId),
            eq("Updated Cloudflare"),
            eq(ProviderConfig.ProviderType.CLOUDFLARE),
            eq(credentials)
        );
    }

    @Test
    @DisplayName("PUT /api/v1/providers/{id} should handle provider not found")
    void testUpdateProviderNotFound() {
        // Given
        Long providerId = 999L;

        Map<String, String> credentials = new HashMap<>();
        credentials.put("apiToken", "token");

        CreateProviderRequest request = new CreateProviderRequest();
        request.setName("Provider");
        request.setType(ProviderConfig.ProviderType.CLOUDFLARE);
        request.setCredentials(credentials);

        when(providerConfigService.updateProvider(eq(providerId), anyString(), any(), anyMap()))
            .thenReturn(Mono.error(new RuntimeException("Provider not found: " + providerId)));

        // When & Then
        webTestClient.put()
            .uri("/api/v1/providers/{id}", providerId)
            .bodyValue(request)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(providerConfigService).updateProvider(eq(providerId), anyString(), any(), anyMap());
    }

    @Test
    @DisplayName("DELETE /api/v1/providers/{id} should delete provider successfully")
    void testDeleteProvider() {
        // Given
        Long providerId = 1L;

        when(providerConfigService.deleteProvider(providerId))
            .thenReturn(Mono.empty());

        // When & Then
        webTestClient.delete()
            .uri("/api/v1/providers/{id}", providerId)
            .exchange()
            .expectStatus().isOk();

        verify(providerConfigService).deleteProvider(providerId);
    }

    @Test
    @DisplayName("DELETE /api/v1/providers/{id} should handle provider not found")
    void testDeleteProviderNotFound() {
        // Given
        Long providerId = 999L;

        when(providerConfigService.deleteProvider(providerId))
            .thenReturn(Mono.error(new RuntimeException("Provider not found: " + providerId)));

        // When & Then
        webTestClient.delete()
            .uri("/api/v1/providers/{id}", providerId)
            .exchange()
            .expectStatus().is5xxServerError();

        verify(providerConfigService).deleteProvider(providerId);
    }

    @Test
    @DisplayName("GET /api/v1/providers should sanitize all sensitive credential fields")
    void testCredentialSanitization() throws Exception {
        // Given
        ProviderConfig provider = ProviderConfig.builder()
            .id(1L)
            .name("Test Provider")
            .type(ProviderConfig.ProviderType.CLOUDFLARE)
            .credentials("encrypted_data")
            .build();

        List<ProviderConfig> providers = List.of(provider);
        Page<ProviderConfig> page = new PageImpl<>(providers, PageRequest.of(0, 10), 1);

        // Credentials with various sensitive fields
        Map<String, String> creds = new HashMap<>();
        creds.put("apiToken", "sensitive_token");
        creds.put("apiKey", "sensitive_key");
        creds.put("apiSecret", "sensitive_secret");
        creds.put("password", "sensitive_password");
        creds.put("email", "not_sensitive@test.com");
        creds.put("accountId", "1234567890");

        when(encryptionService.decrypt("encrypted_data"))
            .thenReturn(objectMapper.writeValueAsString(creds));

        when(providerConfigService.getAllProviders(any(Pageable.class)))
            .thenReturn(Mono.just(page));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/providers")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].credentials.apiToken").isEqualTo("********")
            .jsonPath("$.content[0].credentials.apiKey").isEqualTo("********")
            .jsonPath("$.content[0].credentials.apiSecret").isEqualTo("********")
            .jsonPath("$.content[0].credentials.password").isEqualTo("********")
            .jsonPath("$.content[0].credentials.email").isEqualTo("not_sensitive@test.com")
            .jsonPath("$.content[0].credentials.accountId").isEqualTo("1234567890");

        verify(providerConfigService).getAllProviders(any(Pageable.class));
    }
}
