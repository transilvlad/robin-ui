package com.robin.gateway.auth;

import com.robin.gateway.model.Session;
import com.robin.gateway.model.User;
import com.robin.gateway.model.dto.AuthResponse;
import com.robin.gateway.model.dto.LoginRequest;
import com.robin.gateway.model.dto.TokenResponse;
import com.robin.gateway.repository.SessionRepository;
import com.robin.gateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Authentication service for handling login, logout, and token refresh.
 *
 * @author Robin Gateway Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticate user and generate tokens.
     *
     * @param loginRequest login credentials
     * @param ipAddress client IP address
     * @param userAgent client user agent
     * @return authentication response with tokens
     * @throws BadCredentialsException if credentials are invalid
     * @throws DisabledException if user account is disabled
     */
    @Transactional
    public AuthResponse login(LoginRequest loginRequest, String ipAddress, String userAgent) {
        log.info("Login attempt for user: {}", loginRequest.getUsername());

        // Find user by username
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        // Check if user is enabled
        if (!user.getEnabled()) {
            throw new DisabledException("User account is disabled");
        }

        // Validate password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for user: {}", loginRequest.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // Create session
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        Session session = Session.builder()
                .userId(user.getId())
                .refreshToken(refreshToken)
                .expiresAt(expiresAt)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .revoked(false)
                .build();
        sessionRepository.save(session);

        // Update last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Successful login for user: {}", user.getUsername());

        // Build response
        return AuthResponse.builder()
                .user(AuthResponse.UserDTO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .roles(user.getRoles())
                        .build())
                .tokens(TokenResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .tokenType("Bearer")
                        .expiresIn(1800L) // 30 minutes in seconds
                        .build())
                .permissions(user.getPermissions())
                .build();
    }

    /**
     * Refresh access token using refresh token.
     *
     * @param refreshToken the refresh token
     * @return new token response
     * @throws BadCredentialsException if refresh token is invalid
     */
    @Transactional
    public TokenResponse refreshToken(String refreshToken) {
        log.debug("Token refresh attempt");

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        // Check token type
        String tokenType = jwtTokenProvider.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new BadCredentialsException("Invalid token type");
        }

        // Find session
        Session session = sessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not found"));

        // Validate session
        if (!session.isValid()) {
            log.warn("Attempt to use expired or revoked refresh token");
            throw new BadCredentialsException("Refresh token is expired or revoked");
        }

        // Get user
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Check if user is enabled
        if (!user.getEnabled()) {
            throw new DisabledException("User account is disabled");
        }

        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        log.info("Token refreshed for user: {}", user.getUsername());

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // Keep same refresh token
                .tokenType("Bearer")
                .expiresIn(1800L)
                .build();
    }

    /**
     * Logout user and revoke refresh token.
     *
     * @param refreshToken the refresh token to revoke
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return;
        }

        sessionRepository.findByRefreshToken(refreshToken)
                .ifPresent(session -> {
                    session.revoke();
                    sessionRepository.save(session);
                    log.info("User logged out, session revoked for user ID: {}", session.getUserId());
                });
    }

    /**
     * Revoke all sessions for a user (logout from all devices).
     *
     * @param userId the user ID
     */
    @Transactional
    public void logoutAllDevices(Long userId) {
        int revokedCount = sessionRepository.revokeAllUserSessions(userId, LocalDateTime.now());
        log.info("Revoked {} sessions for user ID: {}", revokedCount, userId);
    }

    /**
     * Get current user details.
     *
     * @param username the username
     * @return auth response with user details (no tokens)
     */
    @Transactional(readOnly = true)
    public AuthResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return AuthResponse.builder()
                .user(AuthResponse.UserDTO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .roles(user.getRoles())
                        .build())
                .permissions(user.getPermissions())
                .build();
    }

    /**
     * Clean up expired sessions (scheduled task).
     */
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30);
        int deletedCount = sessionRepository.deleteExpiredSessions(cutoffTime);
        if (deletedCount > 0) {
            log.info("Cleaned up {} expired sessions", deletedCount);
        }
    }
}
