package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduledDiagnosticTaskListResponse {
    private List<ScheduledDiagnosticTaskResponse> items;
    private Long total;
}
