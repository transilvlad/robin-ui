package com.robin.gateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Token response DTO.
 */
@Builder
@Schema(description = "Token response containing access and refresh tokens")
public record TokenResponse(
    @Schema(description = "JWT access token")
    String accessToken,

    @Schema(description = "JWT refresh token")
    String refreshToken,

    @Schema(description = "Type of token (always Bearer)")
    String tokenType,

    @Schema(description = "Token expiration in seconds")
    Long expiresIn
) {}
