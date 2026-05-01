package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AgentExecutionMemoryListResponse {
    private List<AgentExecutionMemoryResponse> items;
    private long total;
    private int page;
    private int pageSize;
}
