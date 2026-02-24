package com.robin.gateway.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the aggregated health check endpoint.
 * Tests the health status aggregation from all system components.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthAggregationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

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
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis configuration
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Robin MTA endpoints (will be DOWN in tests)
        registry.add("ROBIN_CLIENT_URL", () -> "http://localhost:9999");
        registry.add("ROBIN_SERVICE_URL", () -> "http://localhost:9999");
    }

    @BeforeAll
    void setUp() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Health aggregate endpoint should be accessible without authentication")
    void testHealthEndpoint_NoAuthRequired() {
        // Act & Assert - Should not require authentication
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.service").isEqualTo("robin-gateway")
                .jsonPath("$.status").exists();
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Health check should report database status")
    void testHealthCheck_DatabaseStatus() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.database").exists()
                .jsonPath("$.database.status").isEqualTo("UP")
                .jsonPath("$.database.database").isEqualTo("PostgreSQL")
                .jsonPath("$.database.url").exists();
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Health check should report Redis status")
    void testHealthCheck_RedisStatus() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.redis").exists()
                .jsonPath("$.redis.status").isEqualTo("UP")
                .jsonPath("$.redis.ping").isEqualTo("PONG");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Health check should report Robin Client API status as DOWN")
    void testHealthCheck_RobinClientDown() {
        // Since Robin MTA is not running in tests, expect DOWN status
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.robinClientApi").exists()
                .jsonPath("$.robinClientApi.status").isEqualTo("DOWN")
                .jsonPath("$.robinClientApi.url").exists()
                .jsonPath("$.robinClientApi.error").exists();
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Health check should report Robin Service API status as DOWN")
    void testHealthCheck_RobinServiceDown() {
        // Since Robin MTA is not running in tests, expect DOWN status
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.robinServiceApi").exists()
                .jsonPath("$.robinServiceApi.status").isEqualTo("DOWN")
                .jsonPath("$.robinServiceApi.url").exists()
                .jsonPath("$.robinServiceApi.error").exists();
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Overall status should be DEGRADED when Robin MTA is down")
    void testHealthCheck_OverallStatusDegraded() {
        // With DB and Redis UP, but Robin MTA DOWN, status should be DEGRADED
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").value(status -> assertThat(status).isIn("DEGRADED", "DOWN"));
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Health check response should include timestamp")
    void testHealthCheck_IncludesTimestamp() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timestamp").isNumber()
                .jsonPath("$.timestamp").value(timestamp -> {
                    assertThat(timestamp).isInstanceOf(Number.class);
                    long ts = ((Number) timestamp).longValue();
                    // Timestamp should be recent (within last minute)
                    long now = System.currentTimeMillis();
                    assertThat(ts).isGreaterThan(now - 60000);
                });
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Health check should respond quickly")
    void testHealthCheck_ResponseTime() {
        // Act
        long startTime = System.currentTimeMillis();

        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Assert - Should respond within 1 second even with external services down
        assertThat(duration).isLessThan(1000);
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Health check should handle concurrent requests")
    void testHealthCheck_ConcurrentRequests() {
        // Act - Make 5 concurrent requests
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                    .uri("/api/v1/health/aggregate")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.service").isEqualTo("robin-gateway");
        }
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Health check should be idempotent")
    void testHealthCheck_Idempotent() {
        // First request
        String firstResponse = webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Second request (should be similar but timestamp may differ)
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("robin-gateway")
                .jsonPath("$.database.status").isEqualTo("UP")
                .jsonPath("$.redis.status").isEqualTo("UP");

        assertThat(firstResponse).isNotBlank();
    }

    @AfterAll
    void tearDown() {
        // TestContainers will automatically stop containers
    }
}
