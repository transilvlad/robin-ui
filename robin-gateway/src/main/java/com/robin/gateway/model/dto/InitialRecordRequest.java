package com.robin.gateway.model.dto;

import com.robin.gateway.model.DnsRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for initial DNS record creation during domain setup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request for creating an initial DNS record")
public class InitialRecordRequest {
    @Schema(description = "DNS record type")
    @NotNull(message = "Type is required")
    private DnsRecord.RecordType type;
    
    @Schema(description = "Record name (e.g. '@', 'mail', 'www')", example = "mail")
    @NotBlank(message = "Name is required")
    private String name;
    
    @Schema(description = "Record content/value", example = "1.2.3.4")
    @NotBlank(message = "Content is required")
    private String content;
    
    @Schema(description = "TTL in seconds", example = "3600")
    private Integer ttl;

    @Schema(description = "MX record priority", example = "10")
    private Integer priority;

    @Schema(description = "Robin-specific purpose for this record")
    private DnsRecord.RecordPurpose purpose;
}
