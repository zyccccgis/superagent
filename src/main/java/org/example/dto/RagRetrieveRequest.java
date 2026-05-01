package org.example.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RagRetrieveRequest {
    @JsonAlias({"query", "Question", "question"})
    private String text;
    private Integer topK;
    private Float minScore;
}
