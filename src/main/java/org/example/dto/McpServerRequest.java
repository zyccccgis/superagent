package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class McpServerRequest {
    private String serverName;
    private String transportType;
    private String baseUrl;
    private String endpoint;
    private String headersJson;
    private Integer requestTimeoutSeconds;
    private Boolean enabled;
}
