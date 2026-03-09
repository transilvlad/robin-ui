package com.robin.gateway.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String VERSION = "v1";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${ROBIN_ENCRYPTION_KEY:}")
    private String configuredKey;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        if (configuredKey == null || configuredKey.trim().isEmpty()) {
            log.warn("ROBIN_ENCRYPTION_KEY is not set; encryption operations will fail until configured.");
            return;
        }
        this.secretKey = loadSecretKey(configuredKey.trim());
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, requireSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return VERSION + "." + encoder.encodeToString(iv) + "." + encoder.encodeToString(ciphertext);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException("Encrypted value cannot be blank");
        }

        try {
            String[] parts = encryptedValue.split("\\.", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Encrypted value has invalid format");
            }
            if (!VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported encrypted value version: " + parts[0]);
            }

            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] iv = decoder.decode(parts[1]);
            byte[] ciphertext = decoder.decode(parts[2]);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, requireSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }

    private SecretKey requireSecretKey() {
        if (secretKey == null) {
            throw new IllegalStateException("ROBIN_ENCRYPTION_KEY is not configured");
        }
        return secretKey;
    }

    private SecretKey loadSecretKey(String keyValue) {
        byte[] keyBytes = parseKeyBytes(keyValue);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("ROBIN_ENCRYPTION_KEY must resolve to exactly 32 bytes for AES-256");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] parseKeyBytes(String keyValue) {
        if (keyValue.startsWith("base64:")) {
            return decodeBase64(keyValue.substring("base64:".length()));
        }
        if (keyValue.matches("^[0-9a-fA-F]{64}$")) {
            return decodeHex(keyValue);
        }

        byte[] rawBytes = keyValue.getBytes(StandardCharsets.UTF_8);
        if (rawBytes.length == KEY_LENGTH_BYTES) {
            return rawBytes;
        }

        return decodeBase64(keyValue);
    }

    private byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "ROBIN_ENCRYPTION_KEY must be 32-byte raw text, base64, base64:<value>, or 64-char hex",
                    e
            );
        }
    }

    private byte[] decodeHex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }
        return bytes;
    }
}
