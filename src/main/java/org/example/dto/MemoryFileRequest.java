package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryFileRequest {
    private String path;
    private String content;
}
