package com.robin.gateway.model.dto;

import com.robin.gateway.model.DnsProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsProviderRequest {

    @NotBlank
    private String name;

    @NotNull
    private DnsProviderType type;

    @NotNull
    private Map<String, String> credentials;
}
