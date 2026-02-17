package com.robin.gateway.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Alias request DTO.
 */
@Builder
public record AliasRequest(
    @NotBlank(message = "Source email is required")
    @Email(message = "Invalid source email format")
    String source,

    @NotBlank(message = "Destination email is required")
    @Email(message = "Invalid destination email format")
    String destination
) {}
