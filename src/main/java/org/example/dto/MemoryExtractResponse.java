package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryExtractResponse {
    private String targetPath;
    private int extractedCount;
    private String content;
}
