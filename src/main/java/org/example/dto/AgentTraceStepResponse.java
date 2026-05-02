package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTraceStepResponse {
    private Long id;
    private String traceId;
    private String stepType;
    private String stepName;
    private String status;
    private String inputSummary;
    private String outputSummary;
    private Long startTime;
    private Long endTime;
    private Long durationMs;
    private String errorMessage;
}
