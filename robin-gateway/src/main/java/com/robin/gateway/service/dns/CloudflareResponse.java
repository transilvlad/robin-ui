package com.robin.gateway.service.dns;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class CloudflareResponse<T> {
    private boolean success;
    private List<CloudflareError> errors;
    private T result;

    @Data
    public static class CloudflareError {
        private int code;
        private String message;
    }

    @Data
    public static class DnsRecordResult {
        private String id;
        private String type;
        private String name;
        private String content;
        private int ttl;
        private int priority;
    }
}
