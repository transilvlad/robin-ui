package com.robin.gateway.integration;

import com.robin.gateway.model.Domain;
import com.robin.gateway.model.MtaStsWorker;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MTA-STS worker deployment and policy update.
 * DM-103
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MtaStsIntegrationTest {

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
        // Override Cloudflare base URL to WireMock (not started in this test – deploy not called)
        registry.add("cloudflare.api.base-url", () -> "http://localhost:9999");
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

        domainId = webTestClient.post()
                .uri("/api/v1/domains")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(DomainRequest.builder().domain("mta-sts-test.example.com").build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Domain.class)
                .returnResult().getResponseBody()
                .getId();

        assertThat(domainId).isNotNull();
    }

    @Test
    @Order(1)
    @DisplayName("Get MTA-STS status returns 404 when no worker deployed")
    void testGetWorkerStatusNotFound() {
        webTestClient.get()
                .uri("/api/v1/domains/" + domainId + "/mta-sts")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(2)
    @DisplayName("Update policy mode returns 404 when no worker exists")
    void testUpdatePolicyModeNotDeployed() {
        webTestClient.put()
                .uri("/api/v1/domains/" + domainId + "/mta-sts/policy")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("policyMode", "testing"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(3)
    @DisplayName("MTA-STS endpoints require authentication")
    void testMtaStsRequiresAuth() {
        webTestClient.get()
                .uri("/api/v1/domains/" + domainId + "/mta-sts")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
