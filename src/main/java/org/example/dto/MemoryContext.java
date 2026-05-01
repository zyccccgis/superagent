package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MemoryContext {
    private String memoryIndex;
    private List<MemoryFileResponse> topicFiles;
    private String shortMemory;
}
