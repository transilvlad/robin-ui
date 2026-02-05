package com.robin.gateway.controller;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.service.DnsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/dns-records")
@RequiredArgsConstructor
public class DnsRecordController {

    private final DnsRecordService dnsRecordService;

    @PutMapping("/{id}")
    public Mono<DnsRecord> updateRecord(@PathVariable Long id, @RequestBody DnsRecord record) {
        return dnsRecordService.updateRecord(id, record);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteRecord(@PathVariable Long id) {
        return dnsRecordService.deleteRecord(id);
    }
}
