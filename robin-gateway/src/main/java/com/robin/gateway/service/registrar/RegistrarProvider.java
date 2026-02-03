package com.robin.gateway.service.registrar;

import com.robin.gateway.model.Domain;
import java.time.LocalDate;
import java.util.List;

public interface RegistrarProvider {
    DomainInfo getDomainDetails(String domainName);
    void updateNameservers(String domainName, List<String> nameservers);
    
    record DomainInfo(LocalDate renewalDate, List<String> nameservers, String status) {}
}
