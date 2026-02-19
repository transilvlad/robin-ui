package com.robin.gateway.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("LoggingController Tests")
class LoggingControllerTest {

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
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("2026-02-15 ERROR Test log\n2026-02-15 INFO Test log"));

        LoggingController controller = new LoggingController(webClientBuilder);
        ReflectionTestUtils.setField(controller, "robinClientUrl", "http://localhost:8090");

        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/logs should return parsed logs from MTA")
    void testGetLogs() {
        webTestClient.get()
                .uri("/api/v1/logs?limit=10&offset=0")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.entries").isArray()
                .jsonPath("$.entries.length()").isEqualTo(2)
                .jsonPath("$.entries[0].level").isEqualTo("INFO") // Reversed in parseLogs
                .jsonPath("$.entries[1].level").isEqualTo("ERROR");
    }

    @Test
    @DisplayName("GET /api/v1/logs/loggers should return available loggers")
    void testGetLoggers() {
        webTestClient.get()
                .uri("/api/v1/logs/loggers")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$").value(hasItems("com.mimecast.robin", "clamav", "dovecot"));
    }
}
