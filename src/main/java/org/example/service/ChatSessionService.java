package org.example.service;

import org.example.dto.SessionInfoResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChatSessionService {

    ChatSession getOrCreateSession(String sessionId);

    List<Map<String, String>> getHistory(String sessionId);

    void addMessage(String sessionId, String userQuestion, String aiAnswer);

    boolean clearHistory(String sessionId);

    Optional<SessionInfoResponse> getSessionInfo(String sessionId);

    interface ChatSession {
        String getSessionId();

        long getCreateTime();

        List<Map<String, String>> getHistory();

        void addMessage(String userQuestion, String aiAnswer);

        int getMessagePairCount();
    }
}
