package org.example.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatStreamService {

    SseEmitter streamChat(String sessionId, String question);
}
