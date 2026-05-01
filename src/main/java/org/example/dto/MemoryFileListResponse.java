package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MemoryFileListResponse {
    private List<MemoryFileResponse> items;
    private int total;
}
