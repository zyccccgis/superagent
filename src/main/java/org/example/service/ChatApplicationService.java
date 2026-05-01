package org.example.service;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.dto.ChatResponse;

public interface ChatApplicationService {

    ChatResponse chat(String sessionId, String question) throws GraphRunnerException;
}
