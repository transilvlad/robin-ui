package com.robin.gateway.integration;

import com.robin.gateway.model.DnsProvider;
import com.robin.gateway.model.DnsTemplate;
import com.robin.gateway.model.DnsProviderType;
import com.robin.gateway.model.dto.AuthResponse;
import com.robin.gateway.model.dto.DnsProviderRequest;
import com.robin.gateway.model.dto.DnsTemplateRequest;
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
 * Integration tests for DNS template CRUD.
 * DM-106
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DnsTemplateIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private String adminToken;
    private Long createdTemplateId;

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
                .returnResult()
                .getResponseBody()
                .getTokens()
                .getAccessToken();

        assertThat(adminToken).isNotBlank();
    }

    private static final String RECORDS_JSON =
            "[{\"type\":\"MX\",\"name\":\"@\",\"value\":\"mail.example.com\",\"priority\":10}]";

    @Test
    @Order(1)
    @DisplayName("Create DNS template")
    void testCreateTemplate() {
        DnsTemplateRequest request = DnsTemplateRequest.builder()
                .name("mx-template")
                .description("Standard MX record set")
                .records(RECORDS_JSON)
                .build();

        webTestClient.post()
                .uri("/api/v1/dns-templates")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(DnsTemplate.class)
                .value(t -> {
                    assertThat(t.getId()).isNotNull();
                    assertThat(t.getName()).isEqualTo("mx-template");
                    this.createdTemplateId = t.getId();
                });
    }

    @Test
    @Order(2)
    @DisplayName("List DNS templates includes created entry")
    void testListTemplates() {
        webTestClient.get()
                .uri("/api/v1/dns-templates")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DnsTemplate.class)
                .value(list -> assertThat(list).isNotEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("Get DNS template by ID")
    void testGetTemplateById() {
        assertThat(createdTemplateId).isNotNull();

        webTestClient.get()
                .uri("/api/v1/dns-templates/" + createdTemplateId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DnsTemplate.class)
                .value(t -> assertThat(t.getName()).isEqualTo("mx-template"));
    }

    @Test
    @Order(4)
    @DisplayName("Get non-existent template returns 404")
    void testGetTemplateNotFound() {
        webTestClient.get()
                .uri("/api/v1/dns-templates/99999")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(5)
    @DisplayName("Update DNS template")
    void testUpdateTemplate() {
        assertThat(createdTemplateId).isNotNull();

        DnsTemplateRequest update = DnsTemplateRequest.builder()
                .name("mx-template-v2")
                .description("Updated MX template")
                .records(RECORDS_JSON)
                .build();

        webTestClient.put()
                .uri("/api/v1/dns-templates/" + createdTemplateId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DnsTemplate.class)
                .value(t -> assertThat(t.getName()).isEqualTo("mx-template-v2"));
    }

    @Test
    @Order(6)
    @DisplayName("Delete DNS template")
    void testDeleteTemplate() {
        assertThat(createdTemplateId).isNotNull();

        webTestClient.delete()
                .uri("/api/v1/dns-templates/" + createdTemplateId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("DNS template deleted successfully");

        webTestClient.get()
                .uri("/api/v1/dns-templates/" + createdTemplateId)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(7)
    @DisplayName("Unauthenticated access returns 401")
    void testUnauthenticatedAccess() {
        webTestClient.get()
                .uri("/api/v1/dns-templates")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
