package com.robin.gateway.auth;

import com.robin.gateway.model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider Tests")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "test-secret-key-that-must-be-long-enough-for-hs512-algorithm-minimum-64-bytes-long";
    private final long accessExpiration = 3600000; // 1 hour
    private final long refreshExpiration = 604800000; // 7 days

    private User testUser;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secret, accessExpiration, refreshExpiration);
        
        testUser = User.builder()
                .id(1L)
                .username("test@example.com")
                .roles(Set.of("USER"))
                .permissions(Set.of("READ_DOMAINS"))
                .build();
    }

    @Test
    @DisplayName("Should generate valid access token")
    void testGenerateAccessToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo(testUser.getUsername());
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(testUser.getId());
        assertThat(jwtTokenProvider.getTokenType(token)).isEqualTo("access");
    }

    @Test
    @DisplayName("Should generate valid refresh token")
    void testGenerateRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(testUser);
        
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo(testUser.getUsername());
        assertThat(jwtTokenProvider.getTokenType(token)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("Should detect invalid token signature")
    void testInvalidSignature() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        String invalidToken = token + "invalid";
        
        assertThat(jwtTokenProvider.validateToken(invalidToken)).isFalse();
    }

    @Test
    @DisplayName("Should extract all claims")
    void testGetAllClaims() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        Claims claims = jwtTokenProvider.getAllClaimsFromToken(token);
        
        assertThat(claims.getSubject()).isEqualTo(testUser.getUsername());
        assertThat(claims.get("userId", Long.class)).isEqualTo(testUser.getId());
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.get("roles")).isNotNull();
    }

    @Test
    @DisplayName("Should return expiration date")
    void testGetExpirationDate() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        Date expiration = jwtTokenProvider.getExpirationDateFromToken(token);
        
        assertThat(expiration).isAfter(new Date());
    }

    @Test
    @DisplayName("Should check token expiration")
    void testIsTokenExpired() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        assertThat(jwtTokenProvider.isTokenExpired(token)).isFalse();
        
        // Testing actual expiration is hard without a mock clock or very short expiration
        // but we verify the current logic returns false for a new token.
    }
}
