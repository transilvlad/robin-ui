package com.robin.gateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.DnsProviderType;
import com.robin.gateway.model.dto.AuthResponse;
import com.robin.gateway.model.dto.DnsProviderRequest;
import com.robin.gateway.model.dto.LoginRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DNS provider CRUD and test-connection.
 * DM-102
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DnsProviderIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private Long createdProviderId;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("robin_test")
            .withUsername("robin")
            .withPassword("robin")
            .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("robin.service-url", () -> "http://localhost:9999");
    }

    @BeforeAll
    void setUp() {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("admin@robin.local")
                .password("admin123")
                .build();

        adminToken = webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody()
                .getTokens()
                .getAccessToken();

        assertThat(adminToken).isNotBlank();
    }

    @Test
    @Order(1)
    @DisplayName("Create Cloudflare DNS provider")
    void testCreateCloudflareProvider() {
        DnsProviderRequest request = DnsProviderRequest.builder()
                .name("cf-test")
                .type(DnsProviderType.CLOUDFLARE)
                .credentials(Map.of("apiToken", "test-cf-token"))
                .build();

        webTestClient.post()
                .uri("/api/v1/dns-providers")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(DnsProvider.class)
                .value(provider -> {
                    assertThat(provider.getId()).isNotNull();
                    assertThat(provider.getName()).isEqualTo("cf-test");
                    assertThat(provider.getType()).isEqualTo(DnsProviderType.CLOUDFLARE);
                    assertThat(provider.getCredentials()).isEqualTo("****");
                    this.createdProviderId = provider.getId();
                });
    }

    @Test
    @Order(2)
    @DisplayName("List DNS providers includes created provider")
    void testListProviders() {
        webTestClient.get()
                .uri("/api/v1/dns-providers")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DnsProvider.class)
                .value(list -> assertThat(list).isNotEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("Get DNS provider by ID")
    void testGetProviderById() {
        assertThat(createdProviderId).isNotNull();

        webTestClient.get()
                .uri("/api/v1/dns-providers/" + createdProviderId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DnsProvider.class)
                .value(p -> assertThat(p.getId()).isEqualTo(createdProviderId));
    }

    @Test
    @Order(4)
    @DisplayName("Get non-existent provider returns 404")
    void testGetProviderNotFound() {
        webTestClient.get()
                .uri("/api/v1/dns-providers/99999")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(5)
    @DisplayName("Update DNS provider")
    void testUpdateProvider() {
        assertThat(createdProviderId).isNotNull();

        DnsProviderRequest update = DnsProviderRequest.builder()
                .name("cf-test-updated")
                .type(DnsProviderType.CLOUDFLARE)
                .credentials(Map.of("apiToken", "new-token"))
                .build();

        webTestClient.put()
                .uri("/api/v1/dns-providers/" + createdProviderId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DnsProvider.class)
                .value(p -> assertThat(p.getName()).isEqualTo("cf-test-updated"));
    }

    @Test
    @Order(6)
    @DisplayName("Test connection returns connected=true")
    void testConnectionEndpoint() {
        assertThat(createdProviderId).isNotNull();

        webTestClient.post()
                .uri("/api/v1/dns-providers/" + createdProviderId + "/test")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.connected").isEqualTo(true);
    }

    @Test
    @Order(7)
    @DisplayName("Delete provider succeeds")
    void testDeleteProvider() {
        assertThat(createdProviderId).isNotNull();

        webTestClient.delete()
                .uri("/api/v1/dns-providers/" + createdProviderId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk();

        // Verify deletion
        webTestClient.get()
                .uri("/api/v1/dns-providers/" + createdProviderId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(8)
    @DisplayName("Unauthenticated access returns 401")
    void testUnauthenticatedAccess() {
        webTestClient.get()
                .uri("/api/v1/dns-providers")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
