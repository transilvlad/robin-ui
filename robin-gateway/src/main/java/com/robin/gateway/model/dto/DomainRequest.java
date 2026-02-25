package com.robin.gateway.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainRequest {

    @NotBlank(message = "Domain name is required")
    @Pattern(
            regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$",
            message = "Invalid domain name format"
    )
    private String domain;

    private Long dnsProviderId;
    
    private Long nsProviderId;
    
    @Builder.Default
    private boolean existingDomain = false;
}
