package org.example.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ObservedModelCallHook extends ModelHook {

    private final AgentTraceService traceService;
    private final String traceId;
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicReference<Long> currentStepId = new AtomicReference<>();

    public ObservedModelCallHook(AgentTraceService traceService, String traceId) {
        this.traceService = traceService;
        this.traceId = traceId;
    }

    @Override
    public String getName() {
        return "observed_model_call";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        int index = callCount.incrementAndGet();
        Long stepId = traceService.startStep(traceId, "MODEL_CALL", "model-call-" + index, summarizeState(state));
        currentStepId.set(stepId);
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        Long stepId = currentStepId.getAndSet(null);
        traceService.finishStep(stepId, summarizeState(state));
        return CompletableFuture.completedFuture(Map.of());
    }

    private String summarizeState(OverAllState state) {
        if (state == null) {
            return "state=null";
        }
        return state.toString();
    }
}
