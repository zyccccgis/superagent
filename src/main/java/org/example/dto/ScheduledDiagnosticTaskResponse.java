package org.example.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.entity.ScheduledDiagnosticTask;

import java.time.LocalDateTime;

@Getter
@Setter
public class ScheduledDiagnosticTaskResponse {
    private String scheduleId;
    private String taskName;
    private Boolean enabled;
    private String userQuestion;
    private String promptTemplate;
    private Integer intervalMinutes;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private String lastTaskId;
    private String lastStatus;
    private String lastResultSummary;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScheduledDiagnosticTaskResponse fromEntity(ScheduledDiagnosticTask task) {
        ScheduledDiagnosticTaskResponse response = new ScheduledDiagnosticTaskResponse();
        response.setScheduleId(task.getScheduleId());
        response.setTaskName(task.getTaskName());
        response.setEnabled(task.getEnabled() != null && task.getEnabled() == 1);
        response.setUserQuestion(task.getUserQuestion());
        response.setPromptTemplate(task.getPromptTemplate());
        response.setIntervalMinutes(task.getIntervalMinutes());
        response.setNextRunAt(task.getNextRunAt());
        response.setLastRunAt(task.getLastRunAt());
        response.setLastTaskId(task.getLastTaskId());
        response.setLastStatus(task.getLastStatus());
        response.setLastResultSummary(task.getLastResultSummary());
        response.setCreatedBy(task.getCreatedBy());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        return response;
    }
}
