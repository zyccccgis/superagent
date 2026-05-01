package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryCompressRequest {
    private String sessionId;
    private Integer keepRecent;
    private Integer maxRecords;
}
