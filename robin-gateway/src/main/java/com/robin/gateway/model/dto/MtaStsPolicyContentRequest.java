package com.robin.gateway.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MtaStsPolicyContentRequest {

    @NotBlank
    private String content;
}
