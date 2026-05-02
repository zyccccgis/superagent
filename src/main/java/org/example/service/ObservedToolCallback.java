package org.example.service;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

public class ObservedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final AgentTraceService traceService;
    private final String traceId;
    private final String toolSource;

    public ObservedToolCallback(ToolCallback delegate,
                                AgentTraceService traceService,
                                String traceId,
                                String toolSource) {
        this.delegate = delegate;
        this.traceService = traceService;
        this.traceId = traceId;
        this.toolSource = toolSource;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return callObserved(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext tooContext) {
        return callObserved(toolInput, tooContext);
    }

    private String callObserved(String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        Long stepId = traceService.startStep(traceId, "TOOL_CALL", toolName, toolInput);
        long startedAt = System.currentTimeMillis();
        try {
            String result = toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
            long durationMs = System.currentTimeMillis() - startedAt;
            traceService.finishStep(stepId, result);
            traceService.recordToolCall(traceId, stepId, toolName, toolSource, toolInput, result, "SUCCESS", durationMs, null);
            return result;
        } catch (RuntimeException e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            traceService.failStep(stepId, e);
            traceService.recordToolCall(traceId, stepId, toolName, toolSource, toolInput, null, "FAILED", durationMs, e.getMessage());
            throw e;
        }
    }
}
