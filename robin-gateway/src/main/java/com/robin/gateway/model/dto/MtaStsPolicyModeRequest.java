package com.robin.gateway.model.dto;

import com.robin.gateway.model.MtaStsPolicyMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtaStsPolicyModeRequest {

    @NotNull
    private MtaStsPolicyMode policyMode;
}
