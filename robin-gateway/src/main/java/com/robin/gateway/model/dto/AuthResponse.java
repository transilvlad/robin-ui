package com.robin.gateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Authentication response DTO.
 *
 * @author Robin Gateway Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Authentication response containing user info and tokens")
public class AuthResponse {

    @Schema(description = "User profile information")
    private UserDTO user;

    @Schema(description = "Security tokens (access token only in body, refresh token is in cookie)")
    private TokenResponse tokens;

    @Schema(description = "List of user permissions", example = "[\"READ_DOMAINS\", \"WRITE_DOMAINS\"]")
    private Set<String> permissions;

    @Schema(description = "Minimal user information for the UI")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserDTO {
        private Long id;
        private String username;
        private String email;
        private Set<String> roles;
    }
}
