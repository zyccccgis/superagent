package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RagRetrieveResponse {
    private String text;
    private int topK;
    private String scoreType;
    private boolean higherScoreBetter;
    private List<RagRetrieveResult> results;
}
