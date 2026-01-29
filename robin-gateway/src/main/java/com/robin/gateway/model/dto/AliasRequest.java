package com.robin.gateway.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AliasRequest {

    @NotBlank(message = "Source email is required")
    @Email(message = "Invalid source email format")
    private String source;

    @NotBlank(message = "Destination email is required")
    @Email(message = "Invalid destination email format")
    private String destination;
}
