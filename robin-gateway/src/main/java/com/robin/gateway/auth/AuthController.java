package com.robin.gateway.auth;

import com.robin.gateway.model.dto.AuthResponse;
import com.robin.gateway.model.dto.LoginRequest;
import com.robin.gateway.model.dto.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.time.Duration;

/**
 * Authentication REST controller.
 *
 * Provides endpoints for:
 * - User login
 * - Token refresh
 * - User logout
 *
 * @author Robin Gateway Team
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Login endpoint.
     *
     * @param loginRequest login credentials
     * @param request HTTP request
     * @param response HTTP response
     * @return authentication response with tokens
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        return Mono.fromCallable(() -> {
                    String ipAddress = extractIpAddress(request);
                    String userAgent = extractUserAgent(request);

                    AuthResponse authResponse = authService.login(loginRequest, ipAddress, userAgent);

                    // Set refresh token as HttpOnly cookie
                    ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", authResponse.getTokens().getRefreshToken())
                            .httpOnly(true)
                            .secure(true) // Enable in production with HTTPS
                            .path("/")
                            .maxAge(Duration.ofDays(7))
                            .sameSite("Strict")
                            .build();

                    response.addCookie(refreshTokenCookie);

                    // Don't send refresh token in response body
                    authResponse.getTokens().setRefreshToken(null);

                    return ResponseEntity.ok(authResponse);
                })
                .doOnError(e -> log.error("Login error: {}", e.getMessage()));
    }

    /**
     * Refresh token endpoint.
     *
     * @param request HTTP request
     * @return new access token
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenResponse>> refresh(ServerHttpRequest request) {
        return Mono.fromCallable(() -> {
                    String refreshToken = extractRefreshTokenFromCookie(request);

                    if (refreshToken == null) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .<TokenResponse>body(null);
                    }

                    TokenResponse tokenResponse = authService.refreshToken(refreshToken);

                    // Don't send refresh token in response
                    tokenResponse.setRefreshToken(null);

                    return ResponseEntity.ok(tokenResponse);
                })
                .doOnError(e -> log.error("Token refresh error: {}", e.getMessage()));
    }

    /**
     * Logout endpoint.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @return success response
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(
            ServerHttpRequest request,
            ServerHttpResponse response) {

        return Mono.fromCallable(() -> {
                    String refreshToken = extractRefreshTokenFromCookie(request);
                    authService.logout(refreshToken);

                    // Clear refresh token cookie
                    ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
                            .httpOnly(true)
                            .secure(true)
                            .path("/")
                            .maxAge(0)
                            .build();

                    response.addCookie(clearCookie);

                    return ResponseEntity.ok().<Void>build();
                })
                .doOnSuccess(v -> log.info("User logged out successfully"));
    }

    /**
     * Get current user details.
     *
     * @param principal authenticated user principal
     * @return user details
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<AuthResponse>> getCurrentUser(Principal principal) {
        return Mono.fromCallable(() -> {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            return ResponseEntity.ok(authService.getCurrentUser(principal.getName()));
        });
    }

    /**
     * Extract refresh token from HTTP cookie.
     */
    private String extractRefreshTokenFromCookie(ServerHttpRequest request) {
        if (request.getCookies().containsKey("refreshToken")) {
            return request.getCookies().getFirst("refreshToken").getValue();
        }
        return null;
    }

    /**
     * Extract client IP address from request.
     */
    private String extractIpAddress(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            if (request.getRemoteAddress() != null) {
                ip = request.getRemoteAddress().getAddress().getHostAddress();
            }
        }
        return ip;
    }

    /**
     * Extract user agent from request.
     */
    private String extractUserAgent(ServerHttpRequest request) {
        return request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
    }
}
