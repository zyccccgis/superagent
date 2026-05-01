package org.example.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.entity.AgentDiagnosticTask;

import java.time.LocalDateTime;

@Getter
@Setter
public class AIOpsTaskResponse {

    private String taskId;
    private String taskType;
    private String sceneCode;
    private String status;
    private String triggerSource;
    private String sessionId;
    private String requestPayload;
    private String inputSummary;
    private String plannerOutput;
    private String finalReport;
    private String errorCode;
    private String errorMessage;
    private String modelName;
    private String toolSummary;
    private Long durationMs;
    private String createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AIOpsTaskResponse fromEntity(AgentDiagnosticTask task) {
        AIOpsTaskResponse response = new AIOpsTaskResponse();
        response.setTaskId(task.getTaskId());
        response.setTaskType(task.getTaskType());
        response.setSceneCode(task.getSceneCode());
        response.setStatus(task.getStatus());
        response.setTriggerSource(task.getTriggerSource());
        response.setSessionId(task.getSessionId());
        response.setRequestPayload(task.getRequestPayload());
        response.setInputSummary(task.getInputSummary());
        response.setPlannerOutput(task.getPlannerOutput());
        response.setFinalReport(task.getFinalReport());
        response.setErrorCode(task.getErrorCode());
        response.setErrorMessage(task.getErrorMessage());
        response.setModelName(task.getModelName());
        response.setToolSummary(task.getToolSummary());
        response.setDurationMs(task.getDurationMs());
        response.setCreatedBy(task.getCreatedBy());
        response.setStartedAt(task.getStartedAt());
        response.setFinishedAt(task.getFinishedAt());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        return response;
    }
}
