package com.robin.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for EncryptionService.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Encryption/decryption correctness</li>
 *   <li>AES-256-GCM security properties</li>
 *   <li>Key management and validation</li>
 *   <li>Error handling</li>
 *   <li>Edge cases (empty strings, null values)</li>
 *   <li>Tampering detection</li>
 * </ul>
 */
class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private static final String VALID_KEY = EncryptionService.generateKey(); // 256-bit key

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(VALID_KEY);
    }

    // ==================== Basic Encryption/Decryption Tests ====================

    @Test
    @DisplayName("Should encrypt and decrypt simple text correctly")
    void testBasicEncryptDecrypt() {
        String plaintext = "Hello, World!";

        String encrypted = encryptionService.encrypt(plaintext);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt long text correctly")
    void testLongTextEncryptDecrypt() {
        String plaintext = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(100);

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt special characters")
    void testSpecialCharactersEncryptDecrypt() {
        String plaintext = "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?`~\n\t\r";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt Unicode text")
    void testUnicodeEncryptDecrypt() {
        String plaintext = "Unicode: 你好世界 🌍 العالم мир";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt API keys")
    void testApiKeyEncryptDecrypt() {
        String apiKey = "sk_test_dummyKey1234567890";

        String encrypted = encryptionService.encrypt(apiKey);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(apiKey, decrypted);
    }

    // ==================== Security Properties Tests ====================

    @Test
    @DisplayName("Should produce different ciphertext for same plaintext (unique IV)")
    void testUniqueCiphertext() {
        String plaintext = "Same plaintext";

        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        assertNotEquals(encrypted1, encrypted2, "Same plaintext should produce different ciphertext due to unique IV");

        // Both should decrypt to same plaintext
        assertEquals(plaintext, encryptionService.decrypt(encrypted1));
        assertEquals(plaintext, encryptionService.decrypt(encrypted2));
    }

    @Test
    @DisplayName("Should produce Base64-encoded output")
    void testBase64Output() {
        String plaintext = "Test data";
        String encrypted = encryptionService.encrypt(plaintext);

        assertDoesNotThrow(() -> Base64.getDecoder().decode(encrypted),
            "Encrypted output should be valid Base64");
    }

    @Test
    @DisplayName("Encrypted data should be longer than plaintext (IV + tag overhead)")
    void testEncryptionOverhead() {
        String plaintext = "Short";
        String encrypted = encryptionService.encrypt(plaintext);

        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        // IV (12 bytes) + plaintext + tag (16 bytes) should be longer
        assertTrue(encryptedBytes.length > plaintext.length(),
            "Encrypted data should include IV and authentication tag");
    }

    @Test
    @DisplayName("Should detect tampered ciphertext")
    void testTamperingDetection() {
        String plaintext = "Original data";
        String encrypted = encryptionService.encrypt(plaintext);

        // Tamper with the ciphertext (flip one bit)
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        encryptedBytes[encryptedBytes.length - 1] ^= 1; // Flip last bit
        String tamperedEncrypted = Base64.getEncoder().encodeToString(encryptedBytes);

        // Decryption should fail with authentication error
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> encryptionService.decrypt(tamperedEncrypted));
        assertTrue(exception.getMessage().contains("authentication failed"),
            "Should detect tampering via authentication tag");
    }

    @Test
    @DisplayName("Should fail decryption with wrong key")
    void testWrongKeyDecryption() {
        String plaintext = "Secret data";
        String encrypted = encryptionService.encrypt(plaintext);

        // Create new service with different key
        String differentKey = EncryptionService.generateKey();
        EncryptionService wrongKeyService = new EncryptionService(differentKey);

        // Decryption should fail
        assertThrows(RuntimeException.class,
            () -> wrongKeyService.decrypt(encrypted));
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Should handle empty string")
    void testEmptyString() {
        String plaintext = "";

        String encrypted = encryptionService.encrypt(plaintext);
        assertEquals("", encrypted, "Empty string should encrypt to empty string");

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    @DisplayName("Should throw exception for null plaintext")
    void testNullPlaintext() {
        assertThrows(IllegalArgumentException.class,
            () -> encryptionService.encrypt(null));
    }

    @Test
    @DisplayName("Should throw exception for null ciphertext")
    void testNullCiphertext() {
        assertThrows(IllegalArgumentException.class,
            () -> encryptionService.decrypt(null));
    }

    @Test
    @DisplayName("Should handle single character")
    void testSingleCharacter() {
        String plaintext = "A";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should handle whitespace")
    void testWhitespace() {
        String plaintext = "   \t\n\r   ";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    // ==================== Key Management Tests ====================

    @Test
    @DisplayName("Should reject null encryption key")
    void testNullKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new EncryptionService(null));
        assertTrue(exception.getMessage().contains("not configured"));
    }

    @Test
    @DisplayName("Should reject empty encryption key")
    void testEmptyKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new EncryptionService(""));
        assertTrue(exception.getMessage().contains("not configured"));
    }

    @Test
    @DisplayName("Should reject invalid Base64 key")
    void testInvalidBase64Key() {
        assertThrows(IllegalArgumentException.class,
            () -> new EncryptionService("not-valid-base64!!!"));
    }

    @Test
    @DisplayName("Should reject key with wrong size (128-bit)")
    void testWrongKeySizeTooSmall() {
        // Generate 128-bit key (16 bytes)
        byte[] smallKey = new byte[16];
        String smallKeyBase64 = Base64.getEncoder().encodeToString(smallKey);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new EncryptionService(smallKeyBase64));
        assertTrue(exception.getMessage().contains("256 bits"));
    }

    @Test
    @DisplayName("Should reject key with wrong size (512-bit)")
    void testWrongKeySizeTooLarge() {
        // Generate 512-bit key (64 bytes)
        byte[] largeKey = new byte[64];
        String largeKeyBase64 = Base64.getEncoder().encodeToString(largeKey);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new EncryptionService(largeKeyBase64));
        assertTrue(exception.getMessage().contains("256 bits"));
    }

    @Test
    @DisplayName("Should accept valid 256-bit key")
    void testValidKey() {
        String validKey = EncryptionService.generateKey();
        assertDoesNotThrow(() -> new EncryptionService(validKey));
    }

    @Test
    @DisplayName("Generated key should be 256 bits")
    void testGeneratedKeySize() {
        String generatedKey = EncryptionService.generateKey();
        byte[] keyBytes = Base64.getDecoder().decode(generatedKey);

        assertEquals(32, keyBytes.length, "Generated key should be 256 bits (32 bytes)");
    }

    @Test
    @DisplayName("Generated keys should be unique")
    void testGeneratedKeysUnique() {
        String key1 = EncryptionService.generateKey();
        String key2 = EncryptionService.generateKey();

        assertNotEquals(key1, key2, "Generated keys should be cryptographically random");
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should throw exception for malformed ciphertext")
    void testMalformedCiphertext() {
        String malformedCiphertext = Base64.getEncoder().encodeToString(new byte[5]); // Too short

        Exception exception = assertThrows(Exception.class,
            () -> encryptionService.decrypt(malformedCiphertext));
        assertNotNull(exception, "Should throw exception for malformed ciphertext");
    }

    @Test
    @DisplayName("Should throw exception for truncated ciphertext")
    void testTruncatedCiphertext() {
        String plaintext = "Test data";
        String encrypted = encryptionService.encrypt(plaintext);

        // Truncate the ciphertext
        String truncated = encrypted.substring(0, encrypted.length() - 10);

        assertThrows(Exception.class,
            () -> encryptionService.decrypt(truncated));
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Should handle multiple encrypt/decrypt operations")
    void testMultipleOperations() {
        for (int i = 0; i < 100; i++) {
            String plaintext = "Message " + i;
            String encrypted = encryptionService.encrypt(plaintext);
            String decrypted = encryptionService.decrypt(encrypted);
            assertEquals(plaintext, decrypted);
        }
    }

    @Test
    @DisplayName("Should handle concurrent operations safely")
    void testConcurrentOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    String plaintext = "Thread-" + threadId + "-Message-" + i;
                    String encrypted = encryptionService.encrypt(plaintext);
                    String decrypted = encryptionService.decrypt(encrypted);
                    assertEquals(plaintext, decrypted);
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }

    @Test
    @DisplayName("Should encrypt sensitive data formats correctly")
    void testSensitiveDataFormats() {
        // Test various sensitive data formats
        String[] sensitiveData = {
            "sk_test_dummyKey1234567890", // Stripe API key
            "AKIA1234567890DUMMY", // AWS access key
            "AIzaSyD1234567890abcdefghijklmDUMMY", // Google API key
            "ghp_1234567890abcdefghijklmnopqDUMMY", // GitHub PAT
            "xoxb-1234567890-1234567890-abcdefghDUMMY", // Slack token
            "postgres://user:pass@localhost:5432/db", // Database URL
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." // JWT token
        };

        for (String data : sensitiveData) {
            String encrypted = encryptionService.encrypt(data);
            String decrypted = encryptionService.decrypt(encrypted);
            assertEquals(data, decrypted);
        }
    }
}
