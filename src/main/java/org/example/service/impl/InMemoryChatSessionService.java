package org.example.service.impl;

import org.example.dto.SessionInfoResponse;
import org.example.service.ChatSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class InMemoryChatSessionService implements ChatSessionService {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryChatSessionService.class);
    private static final int MAX_WINDOW_SIZE = 6;

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    @Override
    public ChatSession getOrCreateSession(String sessionId) {
        String resolvedSessionId = sessionId;
        if (resolvedSessionId == null || resolvedSessionId.isEmpty()) {
            resolvedSessionId = UUID.randomUUID().toString();
        }
        return sessions.computeIfAbsent(resolvedSessionId, SessionInfo::new);
    }

    @Override
    public List<Map<String, String>> getHistory(String sessionId) {
        return getOrCreateSession(sessionId).getHistory();
    }

    @Override
    public void addMessage(String sessionId, String userQuestion, String aiAnswer) {
        getOrCreateSession(sessionId).addMessage(userQuestion, aiAnswer);
    }

    @Override
    public boolean clearHistory(String sessionId) {
        SessionInfo session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        session.clearHistory();
        return true;
    }

    @Override
    public Optional<SessionInfoResponse> getSessionInfo(String sessionId) {
        SessionInfo session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }

        SessionInfoResponse response = new SessionInfoResponse();
        response.setSessionId(sessionId);
        response.setMessagePairCount(session.getMessagePairCount());
        response.setCreateTime(session.getCreateTime());
        return Optional.of(response);
    }

    private static class SessionInfo implements ChatSession {
        private final String sessionId;
        private final List<Map<String, String>> messageHistory;
        private final long createTime;
        private final ReentrantLock lock;

        SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            this.messageHistory = new ArrayList<>();
            this.createTime = System.currentTimeMillis();
            this.lock = new ReentrantLock();
        }

        @Override
        public void addMessage(String userQuestion, String aiAnswer) {
            lock.lock();
            try {
                Map<String, String> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", userQuestion);
                messageHistory.add(userMsg);

                Map<String, String> assistantMsg = new HashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", aiAnswer);
                messageHistory.add(assistantMsg);

                int maxMessages = MAX_WINDOW_SIZE * 2;
                while (messageHistory.size() > maxMessages) {
                    messageHistory.remove(0);
                    if (!messageHistory.isEmpty()) {
                        messageHistory.remove(0);
                    }
                }

                logger.debug("会话 {} 更新历史消息，当前消息对数: {}", sessionId, messageHistory.size() / 2);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public List<Map<String, String>> getHistory() {
            lock.lock();
            try {
                return new ArrayList<>(messageHistory);
            } finally {
                lock.unlock();
            }
        }

        void clearHistory() {
            lock.lock();
            try {
                messageHistory.clear();
                logger.info("会话 {} 历史消息已清空", sessionId);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int getMessagePairCount() {
            lock.lock();
            try {
                return messageHistory.size() / 2;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public long getCreateTime() {
            return createTime;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }
    }
}
