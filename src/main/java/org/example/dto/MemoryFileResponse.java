package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryFileResponse {
    private String path;
    private String type;
    private String content;
    private Long updatedAt;
    private Long size;
}
