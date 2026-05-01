package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryExtractRequest {
    private String executionId;
    private String sessionId;
    private String targetPath;
    private Integer limit;
}
