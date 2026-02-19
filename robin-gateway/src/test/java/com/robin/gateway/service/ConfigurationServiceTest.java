package com.robin.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("ConfigurationService Tests")
class ConfigurationServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @TempDir
    Path tempDir;

    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup WebClient mock chain
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());

        configurationService = new ConfigurationService(webClientBuilder);

        // Set temporary directory as config path
        ReflectionTestUtils.setField(configurationService, "configPath", tempDir.toString() + "/");
        ReflectionTestUtils.setField(configurationService, "robinServiceUrl", "http://localhost:8080");
    }

    @Test
    @DisplayName("Should read JSON5 configuration file successfully")
    void testGetConfigJson5() throws IOException {
        // Create test JSON5 file
        String section = "storage";
        String json5Content = """
                {
                    // Comment
                    "type": "s3",
                    'bucket': "my-bucket",
                    "region": "us-east-1",
                }
                """;
        Files.writeString(tempDir.resolve(section + ".json5"), json5Content);

        // Execute
        StepVerifier.create(configurationService.getConfig(section))
                .assertNext(config -> {
                    assertThat(config).isNotNull();
                    assertThat(config).containsEntry("type", "s3");
                    assertThat(config).containsEntry("bucket", "my-bucket");
                    assertThat(config).containsEntry("region", "us-east-1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should read JSON configuration file if JSON5 not found")
    void testGetConfigFallbackToJson() throws IOException {
        // Create test JSON file (no JSON5)
        String section = "relay";
        String jsonContent = """
                {
                    "host": "smtp.example.com",
                    "port": 587,
                    "enabled": true
                }
                """;
        Files.writeString(tempDir.resolve(section + ".json"), jsonContent);

        // Execute
        StepVerifier.create(configurationService.getConfig(section))
                .assertNext(config -> {
                    assertThat(config).isNotNull();
                    assertThat(config).containsEntry("host", "smtp.example.com");
                    assertThat(config).containsEntry("port", 587);
                    assertThat(config).containsEntry("enabled", true);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw exception when configuration file not found")
    void testGetConfigFileNotFound() {
        String section = "nonexistent";

        // Execute and verify error
        StepVerifier.create(configurationService.getConfig(section))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Configuration file for 'nonexistent' not found"))
                .verify();
    }

    @Test
    @DisplayName("Should throw exception when configuration file has invalid JSON")
    void testGetConfigInvalidJson() throws IOException {
        String section = "invalid";
        String invalidJson = "{ invalid json content }";
        Files.writeString(tempDir.resolve(section + ".json5"), invalidJson);

        // Execute and verify error
        StepVerifier.create(configurationService.getConfig(section))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Failed to read configuration"))
                .verify();
    }

    @Test
    @DisplayName("Should write configuration file successfully")
    void testUpdateConfig() {
        String section = "webhook";
        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put("url", "https://example.com/webhook");
        newConfig.put("enabled", true);
        newConfig.put("timeout", 30);

        // Execute
        StepVerifier.create(configurationService.updateConfig(section, newConfig))
                .verifyComplete();

        // Verify file was created
        File configFile = tempDir.resolve(section + ".json5").toFile();
        assertThat(configFile).exists();

        // Verify WebClient was called for reload
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("http://localhost:8080/config/reload");
    }

    @Test
    @DisplayName("Should trigger reload after updating configuration")
    void testUpdateConfigTriggersReload() {
        String section = "test";
        Map<String, Object> newConfig = Map.of("key", "value");

        // Execute
        StepVerifier.create(configurationService.updateConfig(section, newConfig))
                .verifyComplete();

        // Verify reload was triggered
        verify(webClient, times(1)).post();
        verify(responseSpec, times(1)).toBodilessEntity();
    }

    @Test
    @DisplayName("Should write empty configuration")
    void testUpdateConfigEmpty() {
        String section = "empty";
        Map<String, Object> emptyConfig = new HashMap<>();

        // Execute
        StepVerifier.create(configurationService.updateConfig(section, emptyConfig))
                .verifyComplete();

        // Verify file exists and is valid
        File configFile = tempDir.resolve(section + ".json5").toFile();
        assertThat(configFile).exists();
    }

    @Test
    @DisplayName("Should overwrite existing configuration")
    void testUpdateConfigOverwrite() throws IOException {
        String section = "existing";

        // Create existing file
        String oldConfig = """
                {
                    "oldKey": "oldValue"
                }
                """;
        Files.writeString(tempDir.resolve(section + ".json5"), oldConfig);

        // Update with new config
        Map<String, Object> newConfig = Map.of("newKey", "newValue");

        // Execute
        StepVerifier.create(configurationService.updateConfig(section, newConfig))
                .verifyComplete();

        // Verify new content
        StepVerifier.create(configurationService.getConfig(section))
                .assertNext(config -> {
                    assertThat(config).containsEntry("newKey", "newValue");
                    assertThat(config).doesNotContainKey("oldKey");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle nested configuration objects")
    void testGetConfigNested() throws IOException {
        String section = "nested";
        String nestedJson = """
                {
                    "database": {
                        "host": "localhost",
                        "port": 5432,
                        "credentials": {
                            "username": "admin",
                            "encrypted": true
                        }
                    }
                }
                """;
        Files.writeString(tempDir.resolve(section + ".json5"), nestedJson);

        // Execute
        StepVerifier.create(configurationService.getConfig(section))
                .assertNext(config -> {
                    assertThat(config).containsKey("database");
                    Map<String, Object> database = (Map<String, Object>) config.get("database");
                    assertThat(database).containsEntry("host", "localhost");
                    assertThat(database).containsEntry("port", 5432);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should initialize config directory if not exists")
    void testInitCreatesDirectory() {
        // Create new service with non-existent directory
        Path newDir = tempDir.resolve("new-config-dir");
        assertThat(newDir.toFile()).doesNotExist();

        ConfigurationService newService = new ConfigurationService(webClientBuilder);
        ReflectionTestUtils.setField(newService, "configPath", newDir.toString() + "/");

        // Call init
        newService.init();

        // Verify directory was created
        assertThat(newDir.toFile()).exists();
        assertThat(newDir.toFile()).isDirectory();
    }

    @Test
    @DisplayName("Should not fail if config directory already exists")
    void testInitWithExistingDirectory() {
        // Directory already exists (tempDir)
        assertThat(tempDir.toFile()).exists();

        ConfigurationService newService = new ConfigurationService(webClientBuilder);
        ReflectionTestUtils.setField(newService, "configPath", tempDir.toString() + "/");

        // Should not throw exception
        newService.init();

        assertThat(tempDir.toFile()).exists();
    }

    @Test
    @DisplayName("Should handle configuration with arrays")
    void testGetConfigWithArrays() throws IOException {
        String section = "arrays";
        String arrayJson = """
                {
                    "servers": ["server1", "server2", "server3"],
                    "ports": [25, 587, 465]
                }
                """;
        Files.writeString(tempDir.resolve(section + ".json5"), arrayJson);

        // Execute
        StepVerifier.create(configurationService.getConfig(section))
                .assertNext(config -> {
                    assertThat(config).containsKey("servers");
                    assertThat(config).containsKey("ports");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle reload failure gracefully")
    void testUpdateConfigReloadFailure() {
        String section = "reload-fail";
        Map<String, Object> newConfig = Map.of("key", "value");

        // Setup reload to fail
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new RuntimeException("Network error")));

        // Execute - should complete despite reload failure
        StepVerifier.create(configurationService.updateConfig(section, newConfig))
                .verifyComplete();

        // Verify file was still written
        File configFile = tempDir.resolve(section + ".json5").toFile();
        assertThat(configFile).exists();
    }

    @Test
    @DisplayName("Should handle special characters in configuration values")
    void testConfigWithSpecialCharacters() throws IOException {
        String section = "special";
        String specialJson = """
                {
                    "password": "p@ssw0rd!#$",
                    "path": "/var/log/app",
                    "regex": "^[a-zA-Z0-9]+$"
                }
                """;
        Files.writeString(tempDir.resolve(section + ".json5"), specialJson);

        // Execute
        StepVerifier.create(configurationService.getConfig(section))
                .assertNext(config -> {
                    assertThat(config).containsEntry("password", "p@ssw0rd!#$");
                    assertThat(config).containsEntry("path", "/var/log/app");
                    assertThat(config).containsEntry("regex", "^[a-zA-Z0-9]+$");
                })
                .verifyComplete();
    }
}
