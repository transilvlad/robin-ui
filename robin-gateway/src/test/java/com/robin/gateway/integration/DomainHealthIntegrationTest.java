package com.robin.gateway.integration;

import com.robin.gateway.model.Domain;
import com.robin.gateway.model.DomainHealth;
import com.robin.gateway.model.dto.AuthResponse;
import com.robin.gateway.model.dto.DomainRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for domain health check endpoints.
 * DM-105
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DomainHealthIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private String adminToken;
    private Long domainId;

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
        adminToken = webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LoginRequest.builder()
                        .username("admin@robin.local")
                        .password("admin123")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult().getResponseBody()
                .getTokens().getAccessToken();

        assertThat(adminToken).isNotBlank();

        // Create a domain for health tests
        domainId = webTestClient.post()
                .uri("/api/v1/domains")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(DomainRequest.builder().domain("health-test.example.com").build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Domain.class)
                .returnResult().getResponseBody()
                .getId();

        assertThat(domainId).isNotNull();
    }

    @Test
    @Order(1)
    @DisplayName("Get domain health returns list (may be empty initially)")
    void testGetDomainHealth() {
        webTestClient.get()
                .uri("/api/v1/domains/" + domainId + "/health")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DomainHealth.class)
                .value(checks -> assertThat(checks).isNotNull());
    }

    @Test
    @Order(2)
    @DisplayName("On-demand verify triggers health checks")
    void testOnDemandVerify() {
        webTestClient.post()
                .uri("/api/v1/domains/" + domainId + "/health/verify")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DomainHealth.class)
                .value(checks -> {
                    // Verification may return results or empty; just confirm no server error
                    assertThat(checks).isNotNull();
                });
    }

    @Test
    @Order(3)
    @DisplayName("Health check for non-existent domain returns 404 or empty")
    void testHealthForNonExistentDomain() {
        webTestClient.get()
                .uri("/api/v1/domains/99999/health")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().value(status ->
                        assertThat(status).isIn(200, 404));
    }

    @Test
    @Order(4)
    @DisplayName("Health endpoint requires authentication")
    void testHealthRequiresAuth() {
        webTestClient.get()
                .uri("/api/v1/domains/" + domainId + "/health")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
