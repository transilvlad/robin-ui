package com.robin.gateway.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * Integration tests for CORS (Cross-Origin Resource Sharing) configuration.
 * Tests preflight requests and actual CORS requests from the Angular UI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CorsIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String FORBIDDEN_ORIGIN = "http://malicious-site.com";

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
        registry.add("ROBIN_CLIENT_URL", () -> "http://localhost:9999");
        registry.add("ROBIN_SERVICE_URL", () -> "http://localhost:9999");
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Preflight request with allowed origin should succeed")
    void testPreflightRequest_AllowedOrigin() {
        // Act & Assert
        webTestClient.options()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
                .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Actual POST request with allowed origin should include CORS headers")
    void testActualRequest_AllowedOrigin() {
        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"username\":\"test\",\"password\":\"test\"}")
                .exchange()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Actual GET request with allowed origin should include CORS headers")
    void testGetRequest_AllowedOrigin() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Request without origin header should not include CORS headers")
    void testRequest_NoOriginHeader() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Preflight request should allow common HTTP methods")
    void testPreflightRequest_AllowedMethods() {
        // Act & Assert
        webTestClient.options()
                .uri("/api/v1/domains")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, methods -> {
                    assertThat(methods).contains("GET");
                    assertThat(methods).contains("POST");
                    assertThat(methods).contains("PUT");
                    assertThat(methods).contains("DELETE");
                });
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Preflight request should allow Authorization header")
    void testPreflightRequest_AuthorizationHeader() {
        // Act & Assert
        webTestClient.options()
                .uri("/api/v1/domains")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, headers -> {
                    assertThat(headers.toLowerCase()).contains("authorization");
                });
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Preflight request should allow Content-Type header")
    void testPreflightRequest_ContentTypeHeader() {
        // Act & Assert
        webTestClient.options()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, headers -> {
                    assertThat(headers.toLowerCase()).contains("content-type");
                });
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: CORS should allow credentials (cookies)")
    void testCors_AllowCredentials() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/health/aggregate")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Preflight request should have appropriate max age")
    void testPreflightRequest_MaxAge() {
        // Act & Assert
        webTestClient.options()
                .uri("/api/v1/domains")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_MAX_AGE)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_MAX_AGE, maxAge -> {
                    long age = Long.parseLong(maxAge);
                    // Should be at least 1 hour (3600 seconds)
                    assertThat(age).isGreaterThanOrEqualTo(3600);
                });
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Multiple CORS requests should be consistent")
    void testCors_ConsistentBehavior() {
        // Make multiple requests and verify consistent CORS behavior
        for (int i = 0; i < 3; i++) {
            webTestClient.get()
                    .uri("/api/v1/health/aggregate")
                    .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Preflight for DELETE method should succeed")
    void testPreflightRequest_DeleteMethod() {
        // Act & Assert
        webTestClient.options()
                .uri("/api/v1/domains/1")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, methods -> {
                    assertThat(methods).contains("DELETE");
                });
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Preflight for PUT method should succeed")
    void testPreflightRequest_PutMethod() {
        // Act & Assert
        webTestClient.options()
                .uri("/api/v1/domains/aliases/1")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, methods -> {
                    assertThat(methods).contains("PUT");
                });
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: CORS should work for all API endpoints")
    void testCors_MultipleEndpoints() {
        String[] endpoints = {
                "/api/v1/health/aggregate",
                "/api/v1/domains",
                "/api/v1/domains/aliases"
        };

        for (String endpoint : endpoints) {
            webTestClient.get()
                    .uri(endpoint)
                    .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                    .exchange()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
        }
    }
}
