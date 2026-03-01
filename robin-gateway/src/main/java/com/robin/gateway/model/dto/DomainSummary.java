package com.robin.gateway.model.dto;

import com.robin.gateway.model.Domain;
import com.robin.gateway.model.DomainHealth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainSummary {
    private Domain domain;
    private List<DomainHealth> healthChecks;
}
