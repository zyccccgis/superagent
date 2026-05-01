package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryCompressResponse {
    private String summaryExecutionId;
    private int compressedCount;
    private String summary;
}
