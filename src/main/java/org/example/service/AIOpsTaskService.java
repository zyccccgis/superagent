package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.AIOpsRequest;
import org.example.dto.AIOpsTaskResponse;
import org.example.entity.AgentDiagnosticTask;
import org.example.entity.DiagnosticTaskStatus;
import org.example.mapper.AgentDiagnosticTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class AIOpsTaskService {

    private static final Logger logger = LoggerFactory.getLogger(AIOpsTaskService.class);

    private static final String TASK_TYPE = "AI_OPS";
    private static final String SCENE_CODE = "AI_OPS";

    @Autowired
    private AgentDiagnosticTaskMapper taskMapper;

    @Autowired
    private ChatService chatService;

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired(required = false)
    private ToolCallbackProvider tools;

    @Autowired
    private ObjectMapper objectMapper;

    public AIOpsTaskResponse createTask(AIOpsRequest request) {
        AIOpsRequest normalizedRequest = normalizeRequest(request);
        LocalDateTime now = LocalDateTime.now();

        AgentDiagnosticTask task = new AgentDiagnosticTask();
        task.setTaskId(generateTaskId());
        task.setTaskType(TASK_TYPE);
        task.setSceneCode(SCENE_CODE);
        task.setStatus(DiagnosticTaskStatus.RUNNING.name());
        task.setTriggerSource(normalizedRequest.getTriggerSource());
        task.setSessionId(normalizedRequest.getSessionId());
        task.setRequestPayload(toRequestPayload(normalizedRequest));
        task.setInputSummary(buildInputSummary(normalizedRequest));
        task.setStartedAt(now);
        task.setCreatedBy(normalizedRequest.getCreatedBy());
        task.setModelName(DashScopeChatModel.DEFAULT_MODEL_NAME);

        taskMapper.insert(task);
        executeTaskAsync(task.getTaskId(), normalizedRequest);

        return AIOpsTaskResponse.fromEntity(task);
    }

    public AIOpsTaskResponse getTask(String taskId) {
        AgentDiagnosticTask task = taskMapper.selectOne(new LambdaQueryWrapper<AgentDiagnosticTask>()
                .eq(AgentDiagnosticTask::getTaskId, taskId)
                .last("limit 1"));

        if (task == null) {
            throw new NoSuchElementException("诊断任务不存在: " + taskId);
        }

        return AIOpsTaskResponse.fromEntity(task);
    }

    @Async
    public void executeTaskAsync(String taskId, AIOpsRequest request) {
        long startedAt = System.currentTimeMillis();
        try {
            logger.info("开始异步执行 AI Ops 任务, taskId: {}", taskId);

            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .defaultOptions(DashScopeChatOptions.builder()
                            .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                            .withTemperature(0.3)
                            .withMaxToken(8000)
                            .withTopP(0.9)
                            .build())
                    .build();

            ToolCallback[] toolCallbacks = tools == null ? new ToolCallback[0] : tools.getToolCallbacks();
            Optional<OverAllState> stateOptional = aiOpsService.executeAiOpsAnalysis(
                    chatModel,
                    toolCallbacks,
                    request.getUserRequest()
            );

            if (stateOptional.isEmpty()) {
                markFailed(taskId, "EMPTY_RESULT", "多 Agent 编排未获取到有效结果", startedAt);
                return;
            }

            OverAllState state = stateOptional.get();
            Optional<String> plannerOutput = aiOpsService.extractPlannerOutput(state);
            Optional<String> finalReport = aiOpsService.extractFinalReport(state);

            if (finalReport.isPresent()) {
                markSuccess(taskId, plannerOutput.orElse(null), finalReport.get(), startedAt);
            } else {
                markFailed(taskId, "REPORT_NOT_FOUND", "多 Agent 流程已完成，但未能生成最终报告。", startedAt);
            }
        } catch (GraphRunnerException e) {
            logger.error("AI Ops 任务执行失败, taskId: {}", taskId, e);
            markFailed(taskId, "GRAPH_RUNNER_ERROR", e.getMessage(), startedAt);
        } catch (Exception e) {
            logger.error("AI Ops 任务执行异常, taskId: {}", taskId, e);
            markFailed(taskId, "UNEXPECTED_ERROR", e.getMessage(), startedAt);
        }
    }

    private void markSuccess(String taskId, String plannerOutput, String finalReport, long startedAt) {
        AgentDiagnosticTask task = getTaskEntity(taskId);
        task.setStatus(DiagnosticTaskStatus.SUCCESS.name());
        task.setPlannerOutput(plannerOutput);
        task.setFinalReport(finalReport);
        task.setFinishedAt(LocalDateTime.now());
        task.setDurationMs(System.currentTimeMillis() - startedAt);
        task.setToolSummary(buildToolSummary(finalReport));
        task.setErrorCode(null);
        task.setErrorMessage(null);
        taskMapper.updateById(task);
    }

    private void markFailed(String taskId, String errorCode, String errorMessage, long startedAt) {
        AgentDiagnosticTask task = getTaskEntity(taskId);
        task.setStatus(DiagnosticTaskStatus.FAILED.name());
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(LocalDateTime.now());
        task.setDurationMs(System.currentTimeMillis() - startedAt);
        taskMapper.updateById(task);
    }

    private AgentDiagnosticTask getTaskEntity(String taskId) {
        AgentDiagnosticTask task = taskMapper.selectOne(new LambdaQueryWrapper<AgentDiagnosticTask>()
                .eq(AgentDiagnosticTask::getTaskId, taskId)
                .last("limit 1"));
        if (task == null) {
            throw new NoSuchElementException("诊断任务不存在: " + taskId);
        }
        return task;
    }

    private AIOpsRequest normalizeRequest(AIOpsRequest request) {
        AIOpsRequest normalized = request == null ? new AIOpsRequest() : request;
        if (!StringUtils.hasText(normalized.getTriggerSource())) {
            normalized.setTriggerSource("MANUAL");
        }
        return normalized;
    }

    private String buildInputSummary(AIOpsRequest request) {
        if (StringUtils.hasText(request.getUserRequest())) {
            String normalized = request.getUserRequest().trim().replaceAll("\\s+", " ");
            return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
        }
        return "默认 AI Ops 自动诊断任务";
    }

    private String toRequestPayload(AIOpsRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化任务请求失败", e);
        }
    }

    private String buildToolSummary(String finalReport) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("report_length", finalReport == null ? 0 : finalReport.length());
        summary.put("generated_at", LocalDateTime.now().toString());
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            return "{\"report_length\":0}";
        }
    }

    private String generateTaskId() {
        return "diag_" + UUID.randomUUID().toString().replace("-", "");
    }
}
