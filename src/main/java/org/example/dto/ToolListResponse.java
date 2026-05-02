package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ToolListResponse {
    private List<ToolResponse> items;
    private int total;
}
