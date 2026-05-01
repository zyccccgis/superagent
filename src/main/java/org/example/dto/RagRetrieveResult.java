package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RagRetrieveResult {
    private int rank;
    private String chunkId;
    private String content;
    private Float distance;
    private String scoreType;
    private String metadata;
    private String source;
    private String fileName;
}
