package org.example.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.entity.AgentExecutionMemory;

import java.time.ZoneId;

@Getter
@Setter
public class AgentExecutionMemoryResponse {
    private String executionId;
    private String sessionId;
    private String userInput;
    private String agentOutput;
    private String memoryIndexSnapshot;
    private String shortMemorySnapshot;
    private String status;
    private String errorMessage;
    private Long startedAt;
    private Long finishedAt;
    private Long durationMs;
    private Long createdAt;
    private Long updatedAt;

    public static AgentExecutionMemoryResponse fromEntity(AgentExecutionMemory entity) {
        AgentExecutionMemoryResponse response = new AgentExecutionMemoryResponse();
        response.setExecutionId(entity.getExecutionId());
        response.setSessionId(entity.getSessionId());
        response.setUserInput(entity.getUserInput());
        response.setAgentOutput(entity.getAgentOutput());
        response.setMemoryIndexSnapshot(entity.getMemoryIndexSnapshot());
        response.setShortMemorySnapshot(entity.getShortMemorySnapshot());
        response.setStatus(entity.getStatus());
        response.setErrorMessage(entity.getErrorMessage());
        response.setStartedAt(toEpochMillis(entity.getStartedAt()));
        response.setFinishedAt(toEpochMillis(entity.getFinishedAt()));
        response.setDurationMs(entity.getDurationMs());
        response.setCreatedAt(toEpochMillis(entity.getCreatedAt()));
        response.setUpdatedAt(toEpochMillis(entity.getUpdatedAt()));
        return response;
    }

    private static Long toEpochMillis(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
