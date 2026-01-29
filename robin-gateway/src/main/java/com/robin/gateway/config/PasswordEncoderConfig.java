package com.robin.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password encoder configuration.
 *
 * Uses BCrypt with strength 12 for password hashing.
 *
 * @author Robin Gateway Team
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * Create BCrypt password encoder bean.
     *
     * @return password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
