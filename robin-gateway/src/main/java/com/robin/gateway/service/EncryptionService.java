package com.robin.gateway.service;

import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

    // Simple placeholder for encryption to fix build dependency
    public String encrypt(String raw) {
        return raw; // In real implementation, use encryption
    }

    public String decrypt(String encrypted) {
        return encrypted;
    }
}