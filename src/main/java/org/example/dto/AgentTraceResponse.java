package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTraceResponse {
    private Long id;
    private String traceId;
    private String sessionId;
    private String userInput;
    private String status;
    private Long startTime;
    private Long endTime;
    private Long durationMs;
    private String modelName;
    private String errorMessage;
}
