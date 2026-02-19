package com.robin.gateway.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * Domain request DTO.
 */
@Builder
public record DomainRequest(
    @NotBlank(message = "Domain name is required")
    @Pattern(
            regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$",
            message = "Invalid domain name format"
    )
    String domain
) {}
