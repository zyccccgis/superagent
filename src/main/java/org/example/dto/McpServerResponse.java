package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class McpServerResponse {
    private Long id;
    private String serverName;
    private String transportType;
    private String baseUrl;
    private String endpoint;
    private String headersJson;
    private Integer requestTimeoutSeconds;
    private Boolean enabled;
    private String status;
    private String lastError;
    private Long lastConnectedAt;
    private Long createdAt;
    private Long updatedAt;
    private Integer toolCount;
}
