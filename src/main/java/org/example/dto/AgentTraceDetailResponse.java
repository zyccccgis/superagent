package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AgentTraceDetailResponse {
    private AgentTraceResponse trace;
    private List<AgentTraceStepResponse> steps;
    private List<AgentToolCallResponse> toolCalls;
}
