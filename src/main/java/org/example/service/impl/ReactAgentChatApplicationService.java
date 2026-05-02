package org.example.service.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.dto.ChatResponse;
import org.example.dto.MemoryContext;
import org.example.service.AgentExecutionMemoryService;
import org.example.service.AgentTraceService;
import org.example.service.ChatApplicationService;
import org.example.service.ChatService;
import org.example.service.ChatSessionService;
import org.example.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReactAgentChatApplicationService implements ChatApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ReactAgentChatApplicationService.class);

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;
    private final MemoryService memoryService;
    private final AgentExecutionMemoryService executionMemoryService;
    private final AgentTraceService traceService;

    public ReactAgentChatApplicationService(ChatService chatService,
                                            ChatSessionService chatSessionService,
                                            MemoryService memoryService,
                                            AgentExecutionMemoryService executionMemoryService,
                                            AgentTraceService traceService) {
        this.chatService = chatService;
        this.chatSessionService = chatSessionService;
        this.memoryService = memoryService;
        this.executionMemoryService = executionMemoryService;
        this.traceService = traceService;
    }

    @Override
    public ChatResponse chat(String sessionId, String question) throws GraphRunnerException {
        ChatSessionService.ChatSession session = chatSessionService.getOrCreateSession(sessionId);
        List<Map<String, String>> history = session.getHistory();
        logger.info("会话历史消息对数: {}", history.size() / 2);
        long startedAt = System.currentTimeMillis();
        String traceId = traceService.startTrace(session.getSessionId(), question, DashScopeChatModel.DEFAULT_MODEL_NAME);
        Long memoryStepId = traceService.startStep(traceId, "MEMORY_LOAD", "loadContext", question);
        MemoryContext memoryContext;
        try {
            memoryContext = memoryService.loadContext(session.getSessionId(), question, history);
            traceService.finishStep(memoryStepId, summarizeMemoryContext(memoryContext));
        } catch (RuntimeException e) {
            traceService.failStep(memoryStepId, e);
            traceService.failTrace(traceId, e);
            throw e;
        }

        DashScopeApi dashScopeApi = chatService.createDashScopeApi();
        DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
        chatService.logAvailableTools();

        logger.info("开始 ReactAgent 对话（支持自动工具调用）");
        String systemPrompt = chatService.buildSystemPrompt(memoryContext);
        ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt, traceId);
        Long agentStepId = traceService.startStep(traceId, "AGENT_RUN", "ReactAgent.call", question);
        try {
            String fullAnswer = chatService.executeChat(agent, question);
            traceService.finishStep(agentStepId, fullAnswer);
            traceService.finishTrace(traceId);
            session.addMessage(question, fullAnswer);
            executionMemoryService.recordAsync(session.getSessionId(), question, fullAnswer, memoryContext, "SUCCESS", null, startedAt);
            logger.info("已更新会话短期记忆 - SessionId: {}", session.getSessionId());
            return ChatResponse.success(fullAnswer);
        } catch (GraphRunnerException | RuntimeException e) {
            traceService.failStep(agentStepId, e);
            traceService.failTrace(traceId, e);
            executionMemoryService.recordAsync(session.getSessionId(), question, null, memoryContext, "FAILED", e.getMessage(), startedAt);
            throw e;
        }
    }

    private String summarizeMemoryContext(MemoryContext memoryContext) {
        if (memoryContext == null) {
            return "未加载记忆上下文";
        }
        int topicCount = memoryContext.getTopicFiles() == null ? 0 : memoryContext.getTopicFiles().size();
        int indexLength = memoryContext.getMemoryIndex() == null ? 0 : memoryContext.getMemoryIndex().length();
        int shortLength = memoryContext.getShortMemory() == null ? 0 : memoryContext.getShortMemory().length();
        return "topicFiles=" + topicCount + ", memoryIndexChars=" + indexLength + ", shortMemoryChars=" + shortLength;
    }
}
