package com.robin.gateway.model.dto;

import com.robin.gateway.model.DkimAlgorithm;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DkimGenerateRequest {

    @NotNull
    private DkimAlgorithm algorithm;

    private String selector;
}
