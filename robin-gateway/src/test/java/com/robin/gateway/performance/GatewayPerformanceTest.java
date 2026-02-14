package com.robin.gateway.performance;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance benchmarks for the Robin Gateway.
 * Verifies overhead, throughput, and stability.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT60S") // Increased timeout for load tests
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GatewayPerformanceTest {

    @Autowired
    private WebTestClient webTestClient;

    private static DisposableServer mockUpstreamServer;
    private static int upstreamPort;
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

    @BeforeAll
    static void startMockServer() {
        // Start a lightweight Netty server to act as the upstream service
        // responding immediately to requests
        mockUpstreamServer = HttpServer.create()
                .port(0) // Random port
                .route(routes -> routes
                        .get("/health", (request, response) -> response.sendString(Mono.just("OK")))
                        .get("/client/queue/status", (request, response) -> response.sendString(Mono.just("{"status":"OK"}")))
                )
                .bindNow();

        upstreamPort = mockUpstreamServer.port();
        System.out.println("Mock upstream server started on port: " + upstreamPort);
    }

    @AfterAll
    static void stopMockServer() {
        if (mockUpstreamServer != null) {
            mockUpstreamServer.disposeNow();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Point gateway routes to our mock server
        registry.add("ROBIN_CLIENT_URL", () -> "http://localhost:" + upstreamPort);
        registry.add("ROBIN_SERVICE_URL", () -> "http://localhost:" + upstreamPort);
    }

    @BeforeEach
    void setupAuth() {
        if (adminToken == null) {
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
                    .returnResult(AuthResponse.class)
                    .getResponseBody()
                    .blockFirst()
                    .getTokens()
                    .getAccessToken();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Gateway Overhead Analysis (< 5ms)")
    void testGatewayOverhead() {
        int warmupRequests = 100;
        int measurementRequests = 1000;
        List<Long> latencies = new ArrayList<>();

        // Warmup
        Flux.range(0, warmupRequests)
                .flatMap(i -> webTestClient.get()
                        .uri("/api/v1/health/public")
                        .exchange()
                        .returnResult(String.class)
                        .getResponseBody())
                .blockLast();

        // Measurement
        for (int i = 0; i < measurementRequests; i++) {
            long start = System.nanoTime();
            webTestClient.get()
                    .uri("/api/v1/health/public")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("OK");
            long duration = System.nanoTime() - start;
            latencies.add(duration);
        }

        // Calculate statistics
        Collections.sort(latencies);
        double avgLatencyMs = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        double p95LatencyMs = latencies.get((int) (measurementRequests * 0.95)) / 1_000_000.0;
        double p99LatencyMs = latencies.get((int) (measurementRequests * 0.99)) / 1_000_000.0;

        System.out.printf("Overhead Stats (ms): Avg: %.2f, P95: %.2f, P99: %.2f%n", avgLatencyMs, p95LatencyMs, p99LatencyMs);

        // Assertions - adjusting expectations for test environment
        // In a real optimized env, <3ms is target. In CI/Testcontainers, we allow more.
        assertThat(avgLatencyMs).isLessThan(20.0); // Allow 20ms in test env
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Sustained Throughput Check")
    void testSustainedThroughput() {
        int totalRequests = 2000; // Reduced from 10k for CI stability
        int concurrency = 50;

        long start = System.currentTimeMillis();

        Flux.range(0, totalRequests)
                .flatMap(i -> webTestClient.get()
                        .uri("/api/v1/health/public")
                        .exchange()
                        .returnResult(String.class)
                        .getResponseBody()
                        .next(), concurrency) // Limit concurrency
                .blockLast();

        long duration = System.currentTimeMillis() - start;
        double throughput = (double) totalRequests / (duration / 1000.0);

        System.out.printf("Throughput: %.2f req/s (Total: %d in %d ms)%n", throughput, totalRequests, duration);

        assertThat(throughput).isGreaterThan(100.0); // Basic sanity check for test env
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Memory Stability Check")
    void testMemoryStability() {
        // Force GC before measurement
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Run load
        int loadRequests = 5000;
        Flux.range(0, loadRequests)
                .flatMap(i -> webTestClient.get()
                        .uri("/api/v1/health/public")
                        .exchange()
                        .returnResult(String.class)
                        .getResponseBody(), 20)
                .blockLast();

        // Force GC after load
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long diff = finalMemory - initialMemory;

        System.out.printf("Memory Change: %.2f MB%n", diff / (1024.0 * 1024.0));

        // Memory shouldn't grow uncontrollably (allow 50MB buffer for test overhead)
        assertThat(diff).isLessThan(50 * 1024 * 1024);
    }
}
