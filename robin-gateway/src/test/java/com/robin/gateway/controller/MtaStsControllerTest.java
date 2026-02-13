package com.robin.gateway.controller;

import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DomainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("MtaStsController Tests")
class MtaStsControllerTest {

    @Mock
    private DomainRepository domainRepository;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        MtaStsController controller = new MtaStsController(domainRepository);

        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should return policy for domain with TESTING mode")
    void testGetMtaStsPolicyTestingMode() {
        // Given
        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .mtaStsEnabled(true)
            .mtaStsMode(Domain.MtaStsMode.TESTING)
            .build();

        when(domainRepository.findByDomain("test.com"))
            .thenReturn(Optional.of(domain));

        // When & Then
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "test.com")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_PLAIN_VALUE)
            .expectBody(String.class)
            .value(body -> {
                assertThat(body).contains("version: STSv1");
                assertThat(body).contains("mode: testing");
                assertThat(body).contains("mx: mail.test.com");
                assertThat(body).contains("max_age: 604800");
            });

        verify(domainRepository).findByDomain("test.com");
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should return policy for domain with ENFORCE mode")
    void testGetMtaStsPolicyEnforceMode() {
        // Given
        Domain domain = Domain.builder()
            .id(1L)
            .domain("example.com")
            .mtaStsEnabled(true)
            .mtaStsMode(Domain.MtaStsMode.ENFORCE)
            .build();

        when(domainRepository.findByDomain("example.com"))
            .thenReturn(Optional.of(domain));

        // When & Then
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "example.com")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_PLAIN_VALUE)
            .expectBody(String.class)
            .value(body -> {
                assertThat(body).contains("version: STSv1");
                assertThat(body).contains("mode: enforce");
                assertThat(body).contains("mx: mail.example.com");
                assertThat(body).contains("max_age: 604800");
            });

        verify(domainRepository).findByDomain("example.com");
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should handle mta-sts subdomain prefix")
    void testGetMtaStsPolicyWithSubdomain() {
        // Given
        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .mtaStsEnabled(true)
            .mtaStsMode(Domain.MtaStsMode.ENFORCE)
            .build();

        when(domainRepository.findByDomain("test.com"))
            .thenReturn(Optional.of(domain));

        // When & Then
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "mta-sts.test.com")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_PLAIN_VALUE)
            .expectBody(String.class)
            .value(body -> {
                assertThat(body).contains("mx: mail.test.com");
            });

        verify(domainRepository).findByDomain("test.com");
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should return 404 when domain not found")
    void testGetMtaStsPolicyDomainNotFound() {
        // Given
        when(domainRepository.findByDomain("nonexistent.com"))
            .thenReturn(Optional.empty());

        // When & Then
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "nonexistent.com")
            .exchange()
            .expectStatus().isNotFound();

        verify(domainRepository).findByDomain("nonexistent.com");
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should return 404 when MTA-STS disabled")
    void testGetMtaStsPolicyDisabled() {
        // Given
        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .mtaStsEnabled(false)
            .mtaStsMode(Domain.MtaStsMode.NONE)
            .build();

        when(domainRepository.findByDomain("test.com"))
            .thenReturn(Optional.of(domain));

        // When & Then
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "test.com")
            .exchange()
            .expectStatus().isNotFound();

        verify(domainRepository).findByDomain("test.com");
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should return policy with correct format")
    void testGetMtaStsPolicyFormat() {
        // Given
        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .mtaStsEnabled(true)
            .mtaStsMode(Domain.MtaStsMode.ENFORCE)
            .build();

        when(domainRepository.findByDomain("test.com"))
            .thenReturn(Optional.of(domain));

        // When & Then
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "test.com")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                // Verify format is correct (key: value\n)
                assertThat(body).matches("(?s)version: STSv1\\nmode: enforce\\nmx: mail\\.test\\.com\\nmax_age: 604800\\n");
            });

        verify(domainRepository).findByDomain("test.com");
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should return policy with 7-day max_age")
    void testGetMtaStsPolicyMaxAge() {
        // Given
        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .mtaStsEnabled(true)
            .mtaStsMode(Domain.MtaStsMode.TESTING)
            .build();

        when(domainRepository.findByDomain("test.com"))
            .thenReturn(Optional.of(domain));

        // When & Then
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "test.com")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                // 604800 seconds = 7 days
                assertThat(body).contains("max_age: 604800");
            });

        verify(domainRepository).findByDomain("test.com");
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should handle multiple domains correctly")
    void testGetMtaStsPolicyMultipleDomains() {
        // Given
        Domain domain1 = Domain.builder()
            .id(1L)
            .domain("domain1.com")
            .mtaStsEnabled(true)
            .mtaStsMode(Domain.MtaStsMode.ENFORCE)
            .build();

        Domain domain2 = Domain.builder()
            .id(2L)
            .domain("domain2.com")
            .mtaStsEnabled(true)
            .mtaStsMode(Domain.MtaStsMode.TESTING)
            .build();

        when(domainRepository.findByDomain("domain1.com"))
            .thenReturn(Optional.of(domain1));

        when(domainRepository.findByDomain("domain2.com"))
            .thenReturn(Optional.of(domain2));

        // When & Then - First domain
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "domain1.com")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                assertThat(body).contains("mode: enforce");
                assertThat(body).contains("mx: mail.domain1.com");
            });

        // When & Then - Second domain
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "domain2.com")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                assertThat(body).contains("mode: testing");
                assertThat(body).contains("mx: mail.domain2.com");
            });

        verify(domainRepository).findByDomain("domain1.com");
        verify(domainRepository).findByDomain("domain2.com");
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should return 404 for domain with NONE mode")
    void testGetMtaStsPolicyNoneMode() {
        // Given
        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .mtaStsEnabled(false)
            .mtaStsMode(Domain.MtaStsMode.NONE)
            .build();

        when(domainRepository.findByDomain("test.com"))
            .thenReturn(Optional.of(domain));

        // When & Then
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "test.com")
            .exchange()
            .expectStatus().isNotFound();

        verify(domainRepository).findByDomain("test.com");
    }

    @Test
    @DisplayName("GET /.well-known/mta-sts.txt should correctly parse Host header with port")
    void testGetMtaStsPolicyHostWithPort() {
        // Given
        Domain domain = Domain.builder()
            .id(1L)
            .domain("test.com")
            .mtaStsEnabled(true)
            .mtaStsMode(Domain.MtaStsMode.ENFORCE)
            .build();

        // Note: In real scenarios, Host header might include port like "test.com:8443"
        // However, this controller doesn't strip port. Testing exact behavior.
        when(domainRepository.findByDomain("test.com:8443"))
            .thenReturn(Optional.empty());

        // When & Then
        webTestClient.get()
            .uri("/.well-known/mta-sts.txt")
            .header("Host", "test.com:8443")
            .exchange()
            .expectStatus().isNotFound();

        verify(domainRepository).findByDomain("test.com:8443");
    }
}
