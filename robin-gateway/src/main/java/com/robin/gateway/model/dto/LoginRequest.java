package com.robin.gateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Login request DTO.
 */
@Builder
@Schema(description = "Authentication request containing user credentials")
public record LoginRequest(
    @Schema(description = "Username or email address", example = "admin@robin.local")
    @NotBlank(message = "Username is required")
    String username,

    @Schema(description = "Plain text password", example = "admin123")
    @NotBlank(message = "Password is required")
    String password,

    @Schema(description = "Whether to issue a long-lived refresh token")
    Boolean rememberMe
) {}
