package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RagDocumentListResponse {
    private List<RagDocumentResponse> items;
    private int total;
    private int page;
    private int pageSize;
}
