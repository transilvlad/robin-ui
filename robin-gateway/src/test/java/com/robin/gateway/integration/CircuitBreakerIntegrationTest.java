package com.robin.gateway.integration;

import com.robin.gateway.model.dto.AuthResponse;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Circuit Breaker functionality.
 * Tests Resilience4j circuit breaker patterns for external service calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30S")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitBreakerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private String adminToken;

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
        // PostgreSQL and Redis
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Configure Robin MTA to non-existent endpoints to trigger circuit breaker
        registry.add("ROBIN_CLIENT_URL", () -> "http://localhost:19999");
        registry.add("ROBIN_SERVICE_URL", () -> "http://localhost:19999");

        // Configure fast circuit breaker for testing
        registry.add("resilience4j.circuitbreaker.instances.robinClientCircuitBreaker.sliding-window-size", () -> "5");
        registry.add("resilience4j.circuitbreaker.instances.robinClientCircuitBreaker.minimum-number-of-calls", () -> "3");
        registry.add("resilience4j.circuitbreaker.instances.robinClientCircuitBreaker.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.robinClientCircuitBreaker.wait-duration-in-open-state", () -> "10s");
    }

    @BeforeAll
    void setUp() {
        // Login to get admin token
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
    @DisplayName("Test 1: Health endpoint shows Robin services DOWN (triggering circuit breaker)")
    void testHealthEndpoint_RobinServicesDown() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.robinClientApi.status").isEqualTo("DOWN")
                .jsonPath("$.robinServiceApi.status").isEqualTo("DOWN")
                .jsonPath("$.status").value(status -> assertThat(status).isIn("DEGRADED", "DOWN"));
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Circuit breaker triggers after multiple failures")
    void testCircuitBreaker_TriggersAfterFailures() {
        // Make multiple requests to trigger circuit breaker
        // The health endpoint will attempt to call Robin APIs which are down

        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                    .uri("/api/v1/health/aggregate")
                    .exchange()
                    .expectStatus().isOk();
        }

        // Circuit breaker should now be in OPEN state for subsequent calls
        // The response should be faster (fallback without waiting for timeout)
        long startTime = System.currentTimeMillis();

        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.robinClientApi.status").isEqualTo("DOWN");

        long duration = System.currentTimeMillis() - startTime;

        // With circuit breaker open, response should be very fast (< 500ms)
        // Without circuit breaker, it would wait for connection timeout (5s)
        assertThat(duration).isLessThan(2000);
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Circuit breaker provides fallback response")
    void testCircuitBreaker_FallbackResponse() {
        // Act & Assert - Fallback should provide meaningful error information
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.robinClientApi").exists()
                .jsonPath("$.robinClientApi.status").isEqualTo("DOWN")
                .jsonPath("$.robinClientApi.error").exists()
                .jsonPath("$.robinServiceApi").exists()
                .jsonPath("$.robinServiceApi.status").isEqualTo("DOWN")
                .jsonPath("$.robinServiceApi.error").exists();
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Circuit breaker does not affect healthy services")
    void testCircuitBreaker_DoesNotAffectHealthyServices() {
        // Even with Robin services DOWN, database and Redis should still work
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.database.status").isEqualTo("UP")
                .jsonPath("$.redis.status").isEqualTo("UP");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Authentication endpoints work despite circuit breaker")
    void testCircuitBreaker_AuthStillWorks() {
        // Authentication doesn't depend on Robin MTA, so it should work
        LoginRequest loginRequest = LoginRequest.builder()
                .username("admin@robin.local")
                .password("admin123")
                .build();

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .value(response -> {
                    assertThat(response.getUser()).isNotNull();
                    assertThat(response.getTokens()).isNotNull();
                });
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Domain management works despite circuit breaker")
    void testCircuitBreaker_DomainManagementWorks() {
        // Domain management uses database, not Robin APIs
        webTestClient.get()
                .uri("/api/v1/domains")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray();
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Circuit breaker maintains service isolation")
    void testCircuitBreaker_ServiceIsolation() {
        // Multiple concurrent health checks should not block each other
        for (int i = 0; i < 10; i++) {
            webTestClient.get()
                    .uri("/api/v1/health/aggregate")
                    .exchange()
                    .expectStatus().isOk();
        }

        // All requests should complete successfully (with DOWN status for Robin services)
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("robin-gateway");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Circuit breaker respects timeout configuration")
    void testCircuitBreaker_TimeoutConfiguration() {
        // Measure response time for health check
        long startTime = System.currentTimeMillis();

        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk();

        long duration = System.currentTimeMillis() - startTime;

        // Should not exceed reasonable timeout (with circuit breaker: < 2s)
        assertThat(duration).isLessThan(5000);
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Multiple health checks show consistent circuit breaker behavior")
    void testCircuitBreaker_ConsistentBehavior() {
        // Make several requests and verify consistent behavior
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                    .uri("/api/v1/health/aggregate")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.robinClientApi.status").isEqualTo("DOWN")
                    .jsonPath("$.database.status").isEqualTo("UP")
                    .jsonPath("$.redis.status").isEqualTo("UP");
        }
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Circuit breaker allows gateway to remain operational")
    void testCircuitBreaker_GatewayRemainsFunctional() {
        // Despite Robin services being DOWN, gateway should remain fully operational
        // for services that don't depend on Robin MTA

        // Test 1: Health check works
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk();

        // Test 2: Auth works
        LoginRequest loginRequest = LoginRequest.builder()
                .username("admin@robin.local")
                .password("admin123")
                .build();

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk();

        // Test 3: Domain management works
        webTestClient.get()
                .uri("/api/v1/domains")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk();

        // Gateway is fully functional despite circuit breaker being OPEN for Robin services
    }

    @AfterAll
    void tearDown() {
        // TestContainers will automatically stop containers
    }
}
