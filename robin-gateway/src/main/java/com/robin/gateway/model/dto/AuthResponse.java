package com.robin.gateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.Set;

/**
 * Authentication response DTO.
 */
@Builder
@Schema(description = "Authentication response containing user info and tokens")
public record AuthResponse(
    @Schema(description = "User profile information")
    UserDTO user,

    @Schema(description = "Security tokens")
    TokenResponse tokens,

    @Schema(description = "List of user permissions", example = "[\"READ_DOMAINS\", \"WRITE_DOMAINS\"]")
    Set<String> permissions
) {
    @Builder
    @Schema(description = "Minimal user information for the UI")
    public record UserDTO(
        Long id,
        String username,
        String email,
        Set<String> roles
    ) {}
}
