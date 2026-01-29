package com.robin.gateway.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String rawPassword = "password123";
        String encodedPassword = encoder.encode(rawPassword);
        System.out.println("GENERATED_HASH_START");
        System.out.println(encodedPassword);
        System.out.println("GENERATED_HASH_END");
    }
}
