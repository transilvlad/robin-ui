package com.robin.gateway.controller;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.service.DnsRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/dns-records")
@RequiredArgsConstructor
@Tag(name = "DNS Records", description = "APIs for managing individual DNS records")
public class DnsRecordController {

    private final DnsRecordService dnsRecordService;

    @PutMapping("/{id}")
    @Operation(summary = "Update DNS record", description = "Updates an existing DNS record's content, TTL, or priority")
    public Mono<DnsRecord> updateRecord(
            @Parameter(description = "Internal record ID") @PathVariable Long id, 
            @Valid @RequestBody DnsRecord record) {
        return dnsRecordService.updateRecord(id, record);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete DNS record", description = "Removes a DNS record from a domain")
    public Mono<Void> deleteRecord(@Parameter(description = "Internal record ID") @PathVariable Long id) {
        return dnsRecordService.deleteRecord(id);
    }
}
