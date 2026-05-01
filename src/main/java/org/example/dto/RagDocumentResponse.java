package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RagDocumentResponse {
    private String documentId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String extension;
    private String status;
    private Long updatedAt;
}
