package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AIOpsTaskListResponse {
    private List<AIOpsTaskResponse> items;
    private Long total;
}
