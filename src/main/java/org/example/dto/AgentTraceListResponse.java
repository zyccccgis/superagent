package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AgentTraceListResponse {
    private List<AgentTraceResponse> items;
    private Long total;
    private Integer page;
    private Integer pageSize;
}
