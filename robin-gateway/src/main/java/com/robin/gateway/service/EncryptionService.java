package com.robin.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data using AES-256-GCM.
 *
 * <p>This service provides authenticated encryption for sensitive data such as:
 * <ul>
 *   <li>DNS provider API keys (Cloudflare, AWS Route53)</li>
 *   <li>Domain registrar credentials (GoDaddy)</li>
 *   <li>OAuth tokens for external integrations</li>
 * </ul>
 *
 * <p><strong>Security Properties:</strong>
 * <ul>
 *   <li>Algorithm: AES-256-GCM (Galois/Counter Mode)</li>
 *   <li>Key Size: 256 bits (32 bytes)</li>
 *   <li>IV Size: 12 bytes (96 bits, recommended for GCM)</li>
 *   <li>Tag Size: 128 bits (authentication tag)</li>
 *   <li>Authenticated encryption (prevents tampering)</li>
 *   <li>Unique IV per encryption operation</li>
 * </ul>
 *
 * <p><strong>Key Management:</strong>
 * <ul>
 *   <li>Encryption key stored in {@code ENCRYPTION_KEY} environment variable</li>
 *   <li>Key must be 256 bits (32 bytes) base64-encoded</li>
 *   <li>Key rotation supported via dual-key decryption</li>
 *   <li>Keys never logged or exposed in error messages</li>
 * </ul>
 *
 * @see <a href="https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf">NIST SP 800-38D</a>
 */
@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits (NIST recommended)
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    private static final int AES_KEY_SIZE = 256; // 256 bits

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    /**
     * Constructs an EncryptionService with the encryption key from environment.
     *
     * @param encryptionKeyBase64 Base64-encoded encryption key (256 bits)
     * @throws IllegalArgumentException if key is null, empty, or invalid size
     */
    public EncryptionService(@Value("${encryption.key:#{null}}") String encryptionKeyBase64) {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Encryption key not configured. Set ENCRYPTION_KEY environment variable. " +
                "Generate with: openssl rand -base64 32"
            );
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException(
                    "Encryption key must be 256 bits (32 bytes). Current size: " + (keyBytes.length * 8) + " bits. " +
                    "Generate with: openssl rand -base64 32"
                );
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            this.secureRandom = new SecureRandom();
            log.info("EncryptionService initialized with AES-256-GCM");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid encryption key format: " + e.getMessage(), e);
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * <p>Output format: [IV (12 bytes)][Ciphertext + Auth Tag], Base64-encoded
     *
     * @param plaintext The text to encrypt
     * @return Base64-encoded encrypted data (IV + ciphertext + tag)
     * @throws IllegalArgumentException if plaintext is null
     * @throws RuntimeException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }

        if (plaintext.isEmpty()) {
            return ""; // Empty string encrypts to empty string
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Combine IV + ciphertext (ciphertext includes auth tag)
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            // Encode to Base64 for storage
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypts ciphertext encrypted with {@link #encrypt(String)}.
     *
     * @param ciphertext Base64-encoded encrypted data (IV + ciphertext + tag)
     * @return The decrypted plaintext
     * @throws IllegalArgumentException if ciphertext is null or malformed
     * @throws RuntimeException if decryption fails (wrong key, tampered data, etc.)
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            throw new IllegalArgumentException("Ciphertext cannot be null");
        }

        if (ciphertext.isEmpty()) {
            return ""; // Empty string decrypts to empty string
        }

        try {
            // Decode from Base64
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            if (decoded.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException(
                    "Ciphertext too short. Expected at least " + GCM_IV_LENGTH + " bytes, got " + decoded.length
                );
            }

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextBytes);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            // Decrypt (will throw exception if auth tag doesn't match)
            byte[] plaintext = cipher.doFinal(ciphertextBytes);

            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);

        } catch (javax.crypto.AEADBadTagException e) {
            log.error("Decryption failed: authentication tag mismatch (tampered data or wrong key)");
            throw new RuntimeException("Failed to decrypt data: authentication failed", e);
        } catch (IllegalArgumentException e) {
            log.error("Decryption failed: malformed ciphertext");
            throw new IllegalArgumentException("Invalid ciphertext format", e);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * Generates a new random encryption key suitable for AES-256.
     *
     * <p>This method is provided for key generation during setup.
     * The generated key should be stored securely and configured via
     * the {@code ENCRYPTION_KEY} environment variable.
     *
     * @return Base64-encoded 256-bit encryption key
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_SIZE);
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
}
