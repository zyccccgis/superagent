package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatResponse {
    private boolean success;
    private String answer;
    private String errorMessage;

    public static ChatResponse success(String answer) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setAnswer(answer);
        return response;
    }

    public static ChatResponse error(String errorMessage) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
