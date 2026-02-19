package com.robin.gateway.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("MonitoringController Tests")
class MonitoringControllerTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock WebClient chain
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        MonitoringController controller = new MonitoringController(webClientBuilder);
        ReflectionTestUtils.setField(controller, "robinServiceUrl", "http://localhost:8080");

        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/metrics/system should return supplementary stats")
    void testGetSystemStats() {
        Map<String, Object> mtaStats = new HashMap<>();
        mtaStats.put("cpu", Map.of("processors", 4));
        mtaStats.put("memory", Map.of("total", 1000, "used", 500));

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(mtaStats));

        webTestClient.get()
                .uri("/api/v1/metrics/system")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cpu.cores").isEqualTo(4)
                .jsonPath("$.memory.usagePercent").isEqualTo(50.0)
                .jsonPath("$.disk").exists();
    }

    @Test
    @DisplayName("GET /api/v1/metrics/queue should bridge to MTA")
    void testGetQueueStats() {
        Map<String, Object> queueStats = Map.of("size", 100);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(queueStats));

        webTestClient.get()
                .uri("/api/v1/metrics/queue")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.size").isEqualTo(100);
    }
}
