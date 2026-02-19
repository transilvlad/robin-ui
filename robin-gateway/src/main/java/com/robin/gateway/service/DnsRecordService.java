package com.robin.gateway.service;

import com.robin.gateway.model.DnsRecord;
import com.robin.gateway.repository.DnsRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class DnsRecordService {

    private final DnsRecordRepository dnsRecordRepository;

    public Mono<DnsRecord> updateRecord(Long id, DnsRecord update) {
        return Mono.fromCallable(() -> {
            DnsRecord record = dnsRecordRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("DNS Record not found: " + id));

            record.setType(update.getType());
            record.setName(update.getName());
            record.setContent(update.getContent());
            record.setTtl(update.getTtl());
            record.setPriority(update.getPriority());
            record.setPurpose(update.getPurpose());
            record.setSyncStatus(DnsRecord.SyncStatus.PENDING); // Mark for re-sync

            return dnsRecordRepository.save(record);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<Void> deleteRecord(Long id) {
        return Mono.fromCallable(() -> {
            dnsRecordRepository.deleteById(id);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
