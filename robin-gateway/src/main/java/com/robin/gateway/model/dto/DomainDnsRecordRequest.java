package com.robin.gateway.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainDnsRecordRequest {

    @NotBlank
    private String recordType;

    @NotBlank
    private String name;

    @NotBlank
    private String value;

    @Builder.Default
    private Integer ttl = 3600;

    private Integer priority;

    @Builder.Default
    private Boolean managed = true;
}
