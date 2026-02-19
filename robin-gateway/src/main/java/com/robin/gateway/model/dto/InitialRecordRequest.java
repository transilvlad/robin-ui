package com.robin.gateway.model.dto;

import com.robin.gateway.model.DnsRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * DTO for initial DNS record creation during domain setup.
 */
@Builder
@Schema(description = "Request for creating an initial DNS record")
public record InitialRecordRequest(
    @Schema(description = "DNS record type")
    @NotNull(message = "Type is required")
    DnsRecord.RecordType type,
    
    @Schema(description = "Record name (e.g. '@', 'mail', 'www')", example = "mail")
    @NotBlank(message = "Name is required")
    String name,
    
    @Schema(description = "Record content/value", example = "1.2.3.4")
    @NotBlank(message = "Content is required")
    String content,
    
    @Schema(description = "TTL in seconds", example = "3600")
    Integer ttl,

    @Schema(description = "MX record priority", example = "10")
    Integer priority,

    @Schema(description = "Robin-specific purpose for this record")
    DnsRecord.RecordPurpose purpose
) {}
