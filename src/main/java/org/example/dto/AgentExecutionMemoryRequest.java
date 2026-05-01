package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentExecutionMemoryRequest {
    private String executionId;
    private String sessionId;
    private String userInput;
    private String agentOutput;
    private String memoryIndexSnapshot;
    private String shortMemorySnapshot;
    private String status;
    private String errorMessage;
}
