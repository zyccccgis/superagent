package org.example.service.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.example.dto.MemoryContext;
import org.example.dto.SseMessage;
import org.example.service.AgentExecutionMemoryService;
import org.example.service.AgentTraceService;
import org.example.service.ChatService;
import org.example.service.ChatSessionService;
import org.example.service.ChatStreamService;
import org.example.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class SseChatStreamService implements ChatStreamService {

    private static final Logger logger = LoggerFactory.getLogger(SseChatStreamService.class);
    private static final long SSE_TIMEOUT_MS = 300000L;

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;
    private final MemoryService memoryService;
    private final AgentExecutionMemoryService executionMemoryService;
    private final AgentTraceService traceService;
    private final AsyncTaskExecutor chatTaskExecutor;

    public SseChatStreamService(ChatService chatService,
                                ChatSessionService chatSessionService,
                                MemoryService memoryService,
                                AgentExecutionMemoryService executionMemoryService,
                                AgentTraceService traceService,
                                AsyncTaskExecutor chatTaskExecutor) {
        this.chatService = chatService;
        this.chatSessionService = chatSessionService;
        this.memoryService = memoryService;
        this.executionMemoryService = executionMemoryService;
        this.traceService = traceService;
        this.chatTaskExecutor = chatTaskExecutor;
    }

    @Override
    public SseEmitter streamChat(String sessionId, String question) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        chatTaskExecutor.execute(() -> doStreamChat(sessionId, question, emitter));
        return emitter;
    }

    private void doStreamChat(String sessionId, String question, SseEmitter emitter) {
        try {
            logger.info("收到 ReactAgent 对话请求 - SessionId: {}, Question: {}", sessionId, question);

            ChatSessionService.ChatSession session = chatSessionService.getOrCreateSession(sessionId);
            List<Map<String, String>> history = session.getHistory();
            logger.info("ReactAgent 会话历史消息对数: {}", history.size() / 2);
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

            logger.info("开始 ReactAgent 流式对话（支持自动工具调用）");
            String systemPrompt = chatService.buildSystemPrompt(memoryContext);
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt, traceId);
            StringBuilder fullAnswerBuilder = new StringBuilder();
            Long agentStepId = traceService.startStep(traceId, "AGENT_RUN", "ReactAgent.stream", question);

            Flux<NodeOutput> stream = agent.stream(question);
            stream.subscribe(
                    output -> handleStreamOutput(output, emitter, fullAnswerBuilder),
                    error -> handleStreamError(error, emitter, session.getSessionId(), question, memoryContext, startedAt, traceId, agentStepId),
                    () -> handleStreamComplete(session, question, emitter, fullAnswerBuilder, memoryContext, startedAt, traceId, agentStepId)
            );
        } catch (Exception e) {
            logger.error("ReactAgent 对话初始化失败", e);
            sendErrorAndComplete(emitter, e);
        }
    }

    private void handleStreamOutput(NodeOutput output, SseEmitter emitter, StringBuilder fullAnswerBuilder) {
        try {
            if (output instanceof StreamingOutput streamingOutput) {
                OutputType type = streamingOutput.getOutputType();
                if (type == OutputType.AGENT_MODEL_STREAMING) {
                    String chunk = streamingOutput.message().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        fullAnswerBuilder.append(chunk);
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                        logger.info("发送流式内容: {}", chunk);
                    }
                } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                    logger.info("模型输出完成");
                } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                    logger.info("工具调用完成: {}", output.node());
                } else if (type == OutputType.AGENT_HOOK_FINISHED) {
                    logger.debug("Hook 执行完成: {}", output.node());
                }
            }
        } catch (IOException e) {
            logger.error("发送流式消息失败", e);
            throw new RuntimeException(e);
        }
    }

    private void handleStreamError(Throwable error,
                                   SseEmitter emitter,
                                   String sessionId,
                                   String question,
                                   MemoryContext memoryContext,
                                   long startedAt,
                                   String traceId,
                                   Long agentStepId) {
        logger.error("ReactAgent 流式对话失败", error);
        traceService.failStep(agentStepId, error);
        traceService.failTrace(traceId, error);
        executionMemoryService.recordAsync(sessionId, question, null, memoryContext, "FAILED", error.getMessage(), startedAt);
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(SseMessage.error(error.getMessage()), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            logger.error("发送错误消息失败", ex);
        }
        emitter.completeWithError(error);
    }

    private void handleStreamComplete(ChatSessionService.ChatSession session,
                                      String question,
                                      SseEmitter emitter,
                                      StringBuilder fullAnswerBuilder,
                                      MemoryContext memoryContext,
                                      long startedAt,
                                      String traceId,
                                      Long agentStepId) {
        try {
            String fullAnswer = fullAnswerBuilder.toString();
            logger.info("ReactAgent 流式对话完成 - SessionId: {}, 答案长度: {}", session.getSessionId(), fullAnswer.length());

            traceService.finishStep(agentStepId, fullAnswer);
            traceService.finishTrace(traceId);
            session.addMessage(question, fullAnswer);
            executionMemoryService.recordAsync(session.getSessionId(), question, fullAnswer, memoryContext, "SUCCESS", null, startedAt);
            logger.info("已更新会话短期记忆 - SessionId: {}", session.getSessionId());

            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(SseMessage.done(), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            logger.error("发送完成消息失败", e);
            emitter.completeWithError(e);
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

    private void sendErrorAndComplete(SseEmitter emitter, Exception e) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            logger.error("发送错误消息失败", ex);
        }
        emitter.completeWithError(e);
    }
}
