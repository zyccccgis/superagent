package org.example.controller;

import org.example.dto.AIOpsRequest;
import org.example.dto.AIOpsTaskResponse;
import org.example.dto.ApiResponse;
import org.example.dto.ChatRequest;
import org.example.dto.ChatResponse;
import org.example.dto.ClearChatHistoryRequest;
import org.example.dto.SessionInfoResponse;
import org.example.dto.SseMessage;
import org.example.service.AIOpsTaskService;
import org.example.service.ChatApplicationService;
import org.example.service.ChatSessionService;
import org.example.service.ChatStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatApplicationService chatApplicationService;
    private final ChatStreamService chatStreamService;
    private final ChatSessionService chatSessionService;
    private final AIOpsTaskService aiOpsTaskService;

    public ChatController(ChatApplicationService chatApplicationService,
                          ChatStreamService chatStreamService,
                          ChatSessionService chatSessionService,
                          AIOpsTaskService aiOpsTaskService) {
        this.chatApplicationService = chatApplicationService;
        this.chatStreamService = chatStreamService;
        this.chatSessionService = chatSessionService;
        this.aiOpsTaskService = aiOpsTaskService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        try {
            logger.info("收到对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());
            if (isBlank(request.getQuestion())) {
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }

            ChatResponse response = chatApplicationService.chat(request.getId(), request.getQuestion());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@RequestBody ClearChatHistoryRequest request) {
        try {
            logger.info("收到清空会话历史请求 - SessionId: {}", request.getId());
            if (isBlank(request.getId())) {
                return ResponseEntity.ok(ApiResponse.error("会话ID不能为空"));
            }

            boolean cleared = chatSessionService.clearHistory(request.getId());
            if (!cleared) {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success("会话历史已清空"));
        } catch (Exception e) {
            logger.error("清空会话历史失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        if (isBlank(request.getQuestion())) {
            return errorEmitter("问题内容不能为空");
        }
        return chatStreamService.streamChat(request.getId(), request.getQuestion());
    }

    @PostMapping("/ai_ops/tasks")
    public ResponseEntity<ApiResponse<AIOpsTaskResponse>> createAiOpsTask(@RequestBody(required = false) AIOpsRequest request) {
        try {
            AIOpsTaskResponse response = aiOpsTaskService.createTask(request);
            logger.info("已创建 AI Ops 诊断任务, taskId: {}", response.getTaskId());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            logger.error("创建 AI Ops 诊断任务失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/ai_ops/tasks/{taskId}")
    public ResponseEntity<ApiResponse<AIOpsTaskResponse>> getAiOpsTask(@PathVariable String taskId) {
        try {
            AIOpsTaskResponse response = aiOpsTaskService.getTask(taskId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (NoSuchElementException e) {
            logger.warn("查询 AI Ops 诊断任务不存在, taskId: {}", taskId);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("查询 AI Ops 诊断任务失败, taskId: {}", taskId, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId) {
        try {
            logger.info("收到获取会话信息请求 - SessionId: {}", sessionId);
            return chatSessionService.getSessionInfo(sessionId)
                    .map(response -> ResponseEntity.ok(ApiResponse.success(response)))
                    .orElseGet(() -> ResponseEntity.ok(ApiResponse.error("会话不存在")));
        } catch (Exception e) {
            logger.error("获取会话信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    private SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter();
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(SseMessage.error(message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
