package com.robin.gateway.integration;

import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.DkimKeyStatus;
import com.robin.gateway.model.Domain;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DKIM key generate, rotate and retire.
 * DM-104
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DkimIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private String adminToken;
    private Long domainId;
    private Long dkimKeyId;

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

        // Create a domain to use in DKIM tests
        domainId = webTestClient.post()
                .uri("/api/v1/domains")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(DomainRequest.builder().domain("dkim-test.example.com").build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Domain.class)
                .returnResult().getResponseBody()
                .getId();

        assertThat(domainId).isNotNull();
    }

    @Test
    @Order(1)
    @DisplayName("Generate DKIM key for domain")
    void testGenerateDkimKey() {
        Map<String, String> request = Map.of("selector", "default", "algorithm", "RSA_2048");

        webTestClient.post()
                .uri("/api/v1/domains/" + domainId + "/dkim")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(DkimKey.class)
                .value(key -> {
                    assertThat(key.getId()).isNotNull();
                    assertThat(key.getDomainId()).isEqualTo(domainId);
                    assertThat(key.getSelector()).isEqualTo("default");
                    assertThat(key.getStatus()).isEqualTo(DkimKeyStatus.ACTIVE);
                    assertThat(key.getPublicKey()).isNotBlank();
                    this.dkimKeyId = key.getId();
                });
    }

    @Test
    @Order(2)
    @DisplayName("List DKIM keys for domain returns generated key")
    void testListDkimKeys() {
        webTestClient.get()
                .uri("/api/v1/domains/" + domainId + "/dkim")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DkimKey.class)
                .value(keys -> {
                    assertThat(keys).hasSize(1);
                    assertThat(keys.get(0).getStatus()).isEqualTo(DkimKeyStatus.ACTIVE);
                });
    }

    @Test
    @Order(3)
    @DisplayName("Rotate DKIM key creates a new active key")
    void testRotateDkimKey() {
        webTestClient.post()
                .uri("/api/v1/domains/" + domainId + "/dkim/rotate")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DkimKey.class)
                .value(newKey -> {
                    assertThat(newKey.getId()).isNotEqualTo(dkimKeyId);
                    assertThat(newKey.getStatus()).isIn(DkimKeyStatus.ACTIVE, DkimKeyStatus.ROTATING);
                });
    }

    @Test
    @Order(4)
    @DisplayName("Retire old DKIM key marks it retired")
    void testRetireDkimKey() {
        assertThat(dkimKeyId).isNotNull();

        webTestClient.post()
                .uri("/api/v1/domains/" + domainId + "/dkim/" + dkimKeyId + "/retire")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk();

        // Verify old key is retired
        webTestClient.get()
                .uri("/api/v1/domains/" + domainId + "/dkim")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DkimKey.class)
                .value(keys -> {
                    DkimKey old = keys.stream()
                            .filter(k -> k.getId().equals(dkimKeyId))
                            .findFirst()
                            .orElseThrow();
                    assertThat(old.getStatus()).isEqualTo(DkimKeyStatus.RETIRED);
                });
    }

    @Test
    @Order(5)
    @DisplayName("DKIM endpoints require authentication")
    void testDkimRequiresAuth() {
        webTestClient.get()
                .uri("/api/v1/domains/" + domainId + "/dkim")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
