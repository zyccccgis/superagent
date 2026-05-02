package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class McpServerListResponse {
    private List<McpServerResponse> items;
    private int total;
}
