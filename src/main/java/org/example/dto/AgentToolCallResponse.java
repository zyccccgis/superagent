package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentToolCallResponse {
    private Long id;
    private String traceId;
    private Long stepId;
    private String toolName;
    private String toolSource;
    private String requestJson;
    private String responseSummary;
    private String status;
    private Long durationMs;
    private String errorMessage;
    private Long createdAt;
}
