package org.example.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {
    @JsonProperty(value = "Id")
    @JsonAlias({"id", "ID"})
    private String id;

    @JsonProperty(value = "Question")
    @JsonAlias({"question", "QUESTION"})
    private String question;
}
