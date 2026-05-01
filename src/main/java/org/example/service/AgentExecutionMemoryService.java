package org.example.service;

import org.example.dto.AgentExecutionMemoryListResponse;
import org.example.dto.AgentExecutionMemoryRequest;
import org.example.dto.AgentExecutionMemoryResponse;
import org.example.dto.MemoryContext;

public interface AgentExecutionMemoryService {

    void recordAsync(String sessionId,
                     String userInput,
                     String agentOutput,
                     MemoryContext memoryContext,
                     String status,
                     String errorMessage,
                     long startedAtMillis);

    AgentExecutionMemoryListResponse list(String sessionId, String status, String keyword, Integer page, Integer pageSize);

    AgentExecutionMemoryResponse get(String executionId);

    AgentExecutionMemoryResponse create(AgentExecutionMemoryRequest request);

    AgentExecutionMemoryResponse update(String executionId, AgentExecutionMemoryRequest request);

    void delete(String executionId);

    String recentSnapshot(String sessionId, int limit);
}
