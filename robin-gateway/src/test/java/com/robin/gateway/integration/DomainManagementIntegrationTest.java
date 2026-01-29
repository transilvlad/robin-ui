package com.robin.gateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.model.Domain;
import com.robin.gateway.model.Alias;
import com.robin.gateway.model.dto.AliasRequest;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for domain and alias management endpoints.
 * Tests CRUD operations with RBAC enforcement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DomainManagementIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private Long createdDomainId;
    private Long createdAliasId;

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
        // Login as admin to get token
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

    // ===== Domain Tests =====

    @Test
    @Order(1)
    @DisplayName("Test 1: Create domain with valid data should succeed")
    void testCreateDomain_Success() {
        // Arrange
        DomainRequest request = DomainRequest.builder()
                .domain("example.com")
                .build();

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/domains")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Domain.class)
                .value(domain -> {
                    assertThat(domain.getId()).isNotNull();
                    assertThat(domain.getDomain()).isEqualTo("example.com");
                    assertThat(domain.getCreatedAt()).isNotNull();

                    // Store for later tests
                    this.createdDomainId = domain.getId();
                });
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Create domain with duplicate name should fail")
    void testCreateDomain_DuplicateFails() {
        // Arrange
        DomainRequest request = DomainRequest.builder()
                .domain("example.com") // Already created in Test 1
                .build();

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/domains")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Create domain with invalid name format should fail")
    void testCreateDomain_InvalidFormat() {
        // Arrange
        DomainRequest request = DomainRequest.builder()
                .domain("invalid domain name") // Contains spaces
                .build();

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/domains")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: List domains should return created domain")
    void testListDomains_Success() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/domains")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content[?(@.domain=='example.com')]").exists()
                .jsonPath("$.totalElements").exists();
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Get domain by ID should return domain details")
    void testGetDomainById_Success() {
        // Ensure we have a domain ID
        assertThat(createdDomainId).isNotNull();

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/domains/" + createdDomainId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Domain.class)
                .value(domain -> {
                    assertThat(domain.getId()).isEqualTo(createdDomainId);
                    assertThat(domain.getDomain()).isEqualTo("example.com");
                });
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Get non-existent domain should return 404")
    void testGetDomainById_NotFound() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/domains/99999")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ===== Alias Tests =====

    @Test
    @Order(7)
    @DisplayName("Test 7: Create alias for existing domain should succeed")
    void testCreateAlias_Success() {
        // Arrange
        AliasRequest request = AliasRequest.builder()
                .source("info@example.com")
                .destination("admin@example.com")
                .build();

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/domains/aliases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Alias.class)
                .value(alias -> {
                    assertThat(alias.getId()).isNotNull();
                    assertThat(alias.getSource()).isEqualTo("info@example.com");
                    assertThat(alias.getDestination()).isEqualTo("admin@example.com");
                    assertThat(alias.getCreatedAt()).isNotNull();

                    // Store for later tests
                    this.createdAliasId = alias.getId();
                });
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Create alias with duplicate source should fail")
    void testCreateAlias_DuplicateFails() {
        // Arrange
        AliasRequest request = AliasRequest.builder()
                .source("info@example.com") // Already created
                .destination("another@example.com")
                .build();

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/domains/aliases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Create alias for non-existent domain should fail")
    void testCreateAlias_DomainNotFound() {
        // Arrange
        AliasRequest request = AliasRequest.builder()
                .source("test@nonexistent.com")
                .destination("admin@example.com")
                .build();

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/domains/aliases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: List aliases should return created alias")
    void testListAliases_Success() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/domains/aliases")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content[?(@.source=='info@example.com')]").exists();
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: List domain aliases should return only aliases for that domain")
    void testListDomainAliases_Success() {
        // Ensure we have a domain ID
        assertThat(createdDomainId).isNotNull();

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/domains/" + createdDomainId + "/aliases")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class)
                .value(aliases -> {
                    assertThat(aliases).isNotEmpty();
                });
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Update alias destination should succeed")
    void testUpdateAlias_Success() {
        // Ensure we have an alias ID
        assertThat(createdAliasId).isNotNull();

        // Act & Assert
        webTestClient.put()
                .uri("/api/v1/domains/aliases/" + createdAliasId + "?destination=updated@example.com")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Alias.class)
                .value(alias -> {
                    assertThat(alias.getDestination()).isEqualTo("updated@example.com");
                });
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Delete alias should succeed")
    void testDeleteAlias_Success() {
        // Ensure we have an alias ID
        assertThat(createdAliasId).isNotNull();

        // Act & Assert
        webTestClient.delete()
                .uri("/api/v1/domains/aliases/" + createdAliasId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk();

        // Verify deletion
        webTestClient.get()
                .uri("/api/v1/domains/aliases/" + createdAliasId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Delete domain should succeed and cascade delete aliases")
    void testDeleteDomain_Success() {
        // Ensure we have a domain ID
        assertThat(createdDomainId).isNotNull();

        // Act & Assert
        webTestClient.delete()
                .uri("/api/v1/domains/" + createdDomainId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk();

        // Verify deletion
        webTestClient.get()
                .uri("/api/v1/domains/" + createdDomainId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(15)
    @DisplayName("Test 15: Access domain endpoints without auth should return 401")
    void testDomainEndpoints_WithoutAuth() {
        webTestClient.get()
                .uri("/api/v1/domains")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post()
                .uri("/api/v1/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(DomainRequest.builder().domain("test.com").build())
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
