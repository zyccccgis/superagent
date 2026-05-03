package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduledDiagnosticTaskRequest {
    private String taskName;
    private Boolean enabled;
    private String userQuestion;
    private String promptTemplate;
    private Integer intervalMinutes;
    private String nextRunAt;
    private String createdBy;
}
