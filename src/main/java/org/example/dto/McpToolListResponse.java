package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class McpToolListResponse {
    private List<McpToolResponse> items;
    private int total;
    private Long refreshedAt;
}
