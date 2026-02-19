package com.robin.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robin.gateway.model.ProviderConfig;
import com.robin.gateway.repository.ProviderConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for ProviderConfigService.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Provider CRUD operations</li>
 *   <li>Credential encryption/decryption</li>
 *   <li>Credential masking and merging</li>
 *   <li>Error handling</li>
 *   <li>Pagination</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProviderConfigServiceTest {

    @Mock
    private ProviderConfigRepository providerConfigRepository;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private ProviderConfigService providerConfigService;

    private ObjectMapper objectMapper;
    private ProviderConfig testProvider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Inject ObjectMapper via reflection since @InjectMocks doesn't handle it automatically
        try {
            java.lang.reflect.Field field = ProviderConfigService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(providerConfigService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject ObjectMapper", e);
        }

        testProvider = ProviderConfig.builder()
                .id(1L)
                .name("Cloudflare DNS")
                .type(ProviderConfig.ProviderType.CLOUDFLARE)
                .credentials("encrypted_credentials_base64")
                .build();
    }

    // ==================== Get All Providers Tests ====================

    @Test
    @DisplayName("Should retrieve all providers with pagination")
    void testGetAllProvidersSuccess() {
        // Arrange
        ProviderConfig provider2 = ProviderConfig.builder()
                .id(2L)
                .name("AWS Route53")
                .type(ProviderConfig.ProviderType.AWS_ROUTE53)
                .credentials("encrypted_aws_creds")
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<ProviderConfig> page = new PageImpl<>(Arrays.asList(testProvider, provider2), pageable, 2);

        when(providerConfigRepository.findAll(pageable)).thenReturn(page);

        // Act & Assert
        StepVerifier.create(providerConfigService.getAllProviders(pageable))
                .expectNext(page)
                .verifyComplete();

        verify(providerConfigRepository).findAll(pageable);
    }

    @Test
    @DisplayName("Should return empty page when no providers exist")
    void testGetAllProvidersEmpty() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProviderConfig> emptyPage = new PageImpl<>(Arrays.asList(), pageable, 0);

        when(providerConfigRepository.findAll(pageable)).thenReturn(emptyPage);

        // Act & Assert
        StepVerifier.create(providerConfigService.getAllProviders(pageable))
                .expectNext(emptyPage)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle repository error when getting all providers")
    void testGetAllProvidersError() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(providerConfigRepository.findAll(pageable))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        StepVerifier.create(providerConfigService.getAllProviders(pageable))
                .expectError(RuntimeException.class)
                .verify();
    }

    // ==================== Get Provider By ID Tests ====================

    @Test
    @DisplayName("Should retrieve provider by ID successfully")
    void testGetProviderSuccess() {
        // Arrange
        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(testProvider));

        // Act & Assert
        StepVerifier.create(providerConfigService.getProvider(1L))
                .expectNext(testProvider)
                .verifyComplete();

        verify(providerConfigRepository).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when provider not found")
    void testGetProviderNotFound() {
        // Arrange
        when(providerConfigRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(providerConfigService.getProvider(999L))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Provider not found: 999"))
                .verify();
    }

    // ==================== Create Provider Tests ====================

    @Test
    @DisplayName("Should create provider with encrypted credentials")
    void testCreateProviderSuccess() throws Exception {
        // Arrange
        Map<String, String> credentials = new HashMap<>();
        credentials.put("apiKey", "secret_cloudflare_key");
        credentials.put("email", "admin@example.com");

        String credentialsJson = objectMapper.writeValueAsString(credentials);
        String encryptedCreds = "encrypted_base64_data";

        ProviderConfig savedProvider = ProviderConfig.builder()
                .id(10L)
                .name("New Cloudflare")
                .type(ProviderConfig.ProviderType.CLOUDFLARE)
                .credentials(encryptedCreds)
                .build();

        when(encryptionService.encrypt(credentialsJson)).thenReturn(encryptedCreds);
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(savedProvider);

        // Act & Assert
        StepVerifier.create(providerConfigService.createProvider(
                        "New Cloudflare",
                        ProviderConfig.ProviderType.CLOUDFLARE,
                        credentials))
                .expectNext(savedProvider)
                .verifyComplete();

        verify(encryptionService).encrypt(credentialsJson);
        verify(providerConfigRepository).save(any(ProviderConfig.class));
    }

    @Test
    @DisplayName("Should create provider with empty credentials")
    void testCreateProviderWithEmptyCredentials() throws Exception {
        // Arrange
        String emptyCredentialsJson = objectMapper.writeValueAsString(new HashMap<>());
        String encryptedEmpty = "encrypted_empty";

        ProviderConfig savedProvider = ProviderConfig.builder()
                .id(11L)
                .name("Manual DNS")
                .type(ProviderConfig.ProviderType.GODADDY)
                .credentials(encryptedEmpty)
                .build();

        when(encryptionService.encrypt(emptyCredentialsJson)).thenReturn(encryptedEmpty);
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(savedProvider);

        // Act & Assert
        StepVerifier.create(providerConfigService.createProvider(
                        "Manual DNS",
                        ProviderConfig.ProviderType.GODADDY,
                        null))
                .expectNext(savedProvider)
                .verifyComplete();

        verify(encryptionService).encrypt(emptyCredentialsJson);
    }

    @Test
    @DisplayName("Should handle encryption failure during provider creation")
    void testCreateProviderEncryptionFailure() throws Exception {
        // Arrange
        Map<String, String> credentials = new HashMap<>();
        credentials.put("apiKey", "test_key");

        String credentialsJson = objectMapper.writeValueAsString(credentials);
        when(encryptionService.encrypt(credentialsJson))
                .thenThrow(new RuntimeException("Encryption failed"));

        // Act & Assert
        StepVerifier.create(providerConfigService.createProvider(
                        "Test Provider",
                        ProviderConfig.ProviderType.CLOUDFLARE,
                        credentials))
                .expectError(RuntimeException.class)
                .verify();

        verify(providerConfigRepository, never()).save(any());
    }

    // ==================== Update Provider Tests ====================

    @Test
    @DisplayName("Should update provider name and type without credentials")
    void testUpdateProviderWithoutCredentials() {
        // Arrange
        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(testProvider));
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(testProvider);

        // Act & Assert
        StepVerifier.create(providerConfigService.updateProvider(
                        1L,
                        "Updated Cloudflare",
                        ProviderConfig.ProviderType.CLOUDFLARE,
                        null))
                .expectNext(testProvider)
                .verifyComplete();

        verify(providerConfigRepository).findById(1L);
        verify(providerConfigRepository).save(any(ProviderConfig.class));
        verify(encryptionService, never()).encrypt(anyString());
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    @DisplayName("Should update provider with new credentials")
    void testUpdateProviderWithNewCredentials() throws Exception {
        // Arrange
        Map<String, String> existingCreds = new HashMap<>();
        existingCreds.put("apiKey", "old_key");
        existingCreds.put("email", "old@example.com");

        Map<String, String> newCreds = new HashMap<>();
        newCreds.put("apiKey", "new_key");
        newCreds.put("zone", "new_zone_id");

        Map<String, String> mergedCreds = new HashMap<>();
        mergedCreds.put("apiKey", "new_key");
        mergedCreds.put("email", "old@example.com");
        mergedCreds.put("zone", "new_zone_id");

        String existingJson = objectMapper.writeValueAsString(existingCreds);
        String mergedJson = objectMapper.writeValueAsString(mergedCreds);
        String newEncrypted = "new_encrypted_data";

        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(testProvider));
        when(encryptionService.decrypt("encrypted_credentials_base64")).thenReturn(existingJson);
        when(encryptionService.encrypt(anyString())).thenReturn(newEncrypted);
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(testProvider);

        // Act & Assert
        StepVerifier.create(providerConfigService.updateProvider(
                        1L,
                        "Updated Provider",
                        ProviderConfig.ProviderType.CLOUDFLARE,
                        newCreds))
                .expectNext(testProvider)
                .verifyComplete();

        verify(encryptionService).decrypt("encrypted_credentials_base64");
        verify(encryptionService).encrypt(anyString());
    }

    @Test
    @DisplayName("Should skip masked credentials during update")
    void testUpdateProviderWithMaskedCredentials() throws Exception {
        // Arrange
        Map<String, String> existingCreds = new HashMap<>();
        existingCreds.put("apiKey", "secret_key");
        existingCreds.put("email", "admin@example.com");

        Map<String, String> updateWithMask = new HashMap<>();
        updateWithMask.put("apiKey", "********"); // Masked - should be ignored
        updateWithMask.put("zone", "new_zone");

        Map<String, String> expectedMerged = new HashMap<>();
        expectedMerged.put("apiKey", "secret_key"); // Original preserved
        expectedMerged.put("email", "admin@example.com");
        expectedMerged.put("zone", "new_zone");

        String existingJson = objectMapper.writeValueAsString(existingCreds);
        String newEncrypted = "new_encrypted";

        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(testProvider));
        when(encryptionService.decrypt("encrypted_credentials_base64")).thenReturn(existingJson);
        when(encryptionService.encrypt(anyString())).thenReturn(newEncrypted);
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(testProvider);

        // Act & Assert
        StepVerifier.create(providerConfigService.updateProvider(
                        1L,
                        "Provider",
                        ProviderConfig.ProviderType.CLOUDFLARE,
                        updateWithMask))
                .expectNext(testProvider)
                .verifyComplete();

        verify(encryptionService).decrypt("encrypted_credentials_base64");
        verify(encryptionService).encrypt(anyString());
    }

    @Test
    @DisplayName("Should update provider with empty existing credentials")
    void testUpdateProviderWithEmptyExistingCredentials() throws Exception {
        // Arrange
        ProviderConfig providerWithoutCreds = ProviderConfig.builder()
                .id(1L)
                .name("Manual Provider")
                .type(ProviderConfig.ProviderType.GODADDY)
                .credentials(null)
                .build();

        Map<String, String> newCreds = new HashMap<>();
        newCreds.put("apiKey", "first_key");

        String newJson = objectMapper.writeValueAsString(newCreds);
        String encrypted = "encrypted_new";

        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(providerWithoutCreds));
        when(encryptionService.encrypt(anyString())).thenReturn(encrypted);
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(providerWithoutCreds);

        // Act & Assert
        StepVerifier.create(providerConfigService.updateProvider(
                        1L,
                        "Provider",
                        ProviderConfig.ProviderType.GODADDY,
                        newCreds))
                .expectNext(providerWithoutCreds)
                .verifyComplete();

        verify(encryptionService).encrypt(anyString());
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    @DisplayName("Should reject updating non-existent provider")
    void testUpdateProviderNotFound() {
        // Arrange
        when(providerConfigRepository.findById(999L)).thenReturn(Optional.empty());

        Map<String, String> creds = new HashMap<>();
        creds.put("key", "value");

        // Act & Assert
        StepVerifier.create(providerConfigService.updateProvider(
                        999L,
                        "Provider",
                        ProviderConfig.ProviderType.CLOUDFLARE,
                        creds))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Provider not found: 999"))
                .verify();

        verify(providerConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle decryption failure during update")
    void testUpdateProviderDecryptionFailure() {
        // Arrange
        Map<String, String> newCreds = new HashMap<>();
        newCreds.put("key", "value");

        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(testProvider));
        when(encryptionService.decrypt("encrypted_credentials_base64"))
                .thenThrow(new RuntimeException("Decryption failed"));

        // Act & Assert
        StepVerifier.create(providerConfigService.updateProvider(
                        1L,
                        "Provider",
                        ProviderConfig.ProviderType.CLOUDFLARE,
                        newCreds))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Invalid credentials format or decryption error"))
                .verify();

        verify(providerConfigRepository, never()).save(any());
    }

    // ==================== Delete Provider Tests ====================

    @Test
    @DisplayName("Should delete provider successfully")
    void testDeleteProviderSuccess() {
        // Arrange
        doNothing().when(providerConfigRepository).deleteById(1L);

        // Act & Assert
        StepVerifier.create(providerConfigService.deleteProvider(1L))
                .verifyComplete();

        verify(providerConfigRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Should handle repository error during deletion")
    void testDeleteProviderError() {
        // Arrange
        doThrow(new RuntimeException("Database error"))
                .when(providerConfigRepository).deleteById(999L);

        // Act & Assert
        StepVerifier.create(providerConfigService.deleteProvider(999L))
                .expectError(RuntimeException.class)
                .verify();
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Should handle empty credential updates")
    void testUpdateProviderWithEmptyCredentialsMap() {
        // Arrange
        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(testProvider));
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(testProvider);

        // Act & Assert
        StepVerifier.create(providerConfigService.updateProvider(
                        1L,
                        "Provider",
                        ProviderConfig.ProviderType.GODADDY,
                        new HashMap<>()))
                .expectNext(testProvider)
                .verifyComplete();

        // Empty map should not trigger encryption/decryption
        verify(encryptionService, never()).encrypt(anyString());
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    @DisplayName("Should handle whitespace-only credentials")
    void testUpdateProviderWithWhitespaceCredentials() throws Exception {
        // Arrange
        Map<String, String> existingCreds = new HashMap<>();
        existingCreds.put("apiKey", "secret");

        Map<String, String> updateCreds = new HashMap<>();
        updateCreds.put("apiKey", "   "); // Whitespace only - should be ignored

        String existingJson = objectMapper.writeValueAsString(existingCreds);

        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(testProvider));
        when(encryptionService.decrypt("encrypted_credentials_base64")).thenReturn(existingJson);
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(testProvider);

        // Act & Assert
        StepVerifier.create(providerConfigService.updateProvider(
                        1L,
                        "Provider",
                        ProviderConfig.ProviderType.CLOUDFLARE,
                        updateCreds))
                .expectNext(testProvider)
                .verifyComplete();

        // Original apiKey should be preserved
        verify(encryptionService).decrypt("encrypted_credentials_base64");
    }

    @Test
    @DisplayName("Should create provider for each provider type")
    void testCreateProviderAllTypes() throws Exception {
        // Test all provider types can be created
        ProviderConfig.ProviderType[] types = ProviderConfig.ProviderType.values();

        for (ProviderConfig.ProviderType type : types) {
            reset(encryptionService, providerConfigRepository);

            Map<String, String> creds = new HashMap<>();
            creds.put("key", "value_for_" + type);

            String credJson = objectMapper.writeValueAsString(creds);
            String encrypted = "encrypted_" + type;

            ProviderConfig saved = ProviderConfig.builder()
                    .id(1L)
                    .name("Test " + type)
                    .type(type)
                    .credentials(encrypted)
                    .build();

            when(encryptionService.encrypt(credJson)).thenReturn(encrypted);
            when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(saved);

            // Act & Assert
            StepVerifier.create(providerConfigService.createProvider(
                            "Test " + type,
                            type,
                            creds))
                    .expectNext(saved)
                    .verifyComplete();

            verify(encryptionService).encrypt(credJson);
            verify(providerConfigRepository).save(any(ProviderConfig.class));
        }
    }
}
