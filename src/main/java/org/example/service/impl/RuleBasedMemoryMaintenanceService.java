package org.example.service.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.dto.MemoryCompressRequest;
import org.example.dto.MemoryCompressResponse;
import org.example.dto.MemoryExtractRequest;
import org.example.dto.MemoryExtractResponse;
import org.example.dto.MemoryFileRequest;
import org.example.entity.AgentExecutionMemory;
import org.example.mapper.AgentExecutionMemoryMapper;
import org.example.service.ChatService;
import org.example.service.MemoryMaintenanceService;
import org.example.service.MemoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RuleBasedMemoryMaintenanceService implements MemoryMaintenanceService {

    private static final String DEFAULT_TARGET_PATH = "topics/agent-insights.md";

    private final AgentExecutionMemoryMapper memoryMapper;
    private final MemoryService memoryService;
    private final ChatService chatService;

    public RuleBasedMemoryMaintenanceService(AgentExecutionMemoryMapper memoryMapper,
                                             MemoryService memoryService,
                                             ChatService chatService) {
        this.memoryMapper = memoryMapper;
        this.memoryService = memoryService;
        this.chatService = chatService;
    }

    @Override
    public MemoryExtractResponse extractLongTermMemory(MemoryExtractRequest request) {
        MemoryExtractRequest resolvedRequest = request == null ? new MemoryExtractRequest() : request;
        int limit = resolvedRequest.getLimit() == null ? 10 : Math.max(1, Math.min(resolvedRequest.getLimit(), 50));
        List<AgentExecutionMemory> records = findRecords(resolvedRequest.getExecutionId(), resolvedRequest.getSessionId(), "SUCCESS", limit);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("没有可抽取的成功执行记录");
        }

        LongTermExtraction extraction = extractWithModel(records);
        String targetPath = normalizeTopicPath(extraction.targetPath());
        String extracted = extraction.content();
        String existing = safeReadContent(targetPath);
        MemoryFileRequest updateRequest = new MemoryFileRequest();
        updateRequest.setPath(targetPath);
        updateRequest.setContent(existing + "\n\n" + extracted);
        if (existing.isBlank()) {
            memoryService.createFile(updateRequest);
        } else {
            memoryService.updateFile(updateRequest);
        }
        ensureIndexContains(targetPath, extraction.description(), extraction.keywords());

        MemoryExtractResponse response = new MemoryExtractResponse();
        response.setTargetPath(targetPath);
        response.setExtractedCount(records.size());
        response.setContent(extracted);
        return response;
    }

    @Override
    public MemoryCompressResponse compressShortTermMemory(MemoryCompressRequest request) {
        MemoryCompressRequest resolvedRequest = request == null ? new MemoryCompressRequest() : request;
        int keepRecent = resolvedRequest.getKeepRecent() == null ? 6 : Math.max(0, Math.min(resolvedRequest.getKeepRecent(), 50));
        int maxRecords = resolvedRequest.getMaxRecords() == null ? 30 : Math.max(2, Math.min(resolvedRequest.getMaxRecords(), 200));

        LambdaQueryWrapper<AgentExecutionMemory> wrapper = new LambdaQueryWrapper<AgentExecutionMemory>()
                .ne(AgentExecutionMemory::getStatus, "COMPRESSED")
                .orderByDesc(AgentExecutionMemory::getCreatedAt)
                .last("limit " + (keepRecent + maxRecords));
        if (StringUtils.hasText(resolvedRequest.getSessionId())) {
            wrapper.eq(AgentExecutionMemory::getSessionId, resolvedRequest.getSessionId().trim());
        }
        List<AgentExecutionMemory> records = memoryMapper.selectList(wrapper);
        if (records.size() <= keepRecent) {
            throw new IllegalArgumentException("可压缩的旧短期记忆不足");
        }

        List<AgentExecutionMemory> toCompress = records.subList(keepRecent, records.size());
        Collections.reverse(toCompress);
        String summary = compressWithModel(toCompress);

        AgentExecutionMemory compressed = new AgentExecutionMemory();
        compressed.setExecutionId("compressed_" + UUID.randomUUID().toString().replace("-", ""));
        compressed.setSessionId(StringUtils.hasText(resolvedRequest.getSessionId()) ? resolvedRequest.getSessionId().trim() : "GLOBAL");
        compressed.setUserInput("短期记忆压缩摘要");
        compressed.setAgentOutput(summary);
        compressed.setStatus("COMPRESSED");
        compressed.setStartedAt(LocalDateTime.now());
        compressed.setFinishedAt(LocalDateTime.now());
        compressed.setDurationMs(0L);
        compressed.setCreatedAt(LocalDateTime.now());
        compressed.setUpdatedAt(LocalDateTime.now());
        compressed.setDeleted(0);
        memoryMapper.insert(compressed);

        for (AgentExecutionMemory record : toCompress) {
            memoryMapper.deleteById(record.getId());
        }

        MemoryCompressResponse response = new MemoryCompressResponse();
        response.setSummaryExecutionId(compressed.getExecutionId());
        response.setCompressedCount(toCompress.size());
        response.setSummary(summary);
        return response;
    }

    private List<AgentExecutionMemory> findRecords(String executionId, String sessionId, String status, int limit) {
        LambdaQueryWrapper<AgentExecutionMemory> wrapper = new LambdaQueryWrapper<AgentExecutionMemory>()
                .orderByDesc(AgentExecutionMemory::getCreatedAt)
                .last("limit " + limit);
        if (StringUtils.hasText(executionId)) {
            wrapper.eq(AgentExecutionMemory::getExecutionId, executionId.trim());
        }
        if (StringUtils.hasText(sessionId)) {
            wrapper.eq(AgentExecutionMemory::getSessionId, sessionId.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AgentExecutionMemory::getStatus, status);
        }
        List<AgentExecutionMemory> records = memoryMapper.selectList(wrapper);
        Collections.reverse(records);
        return records;
    }

    private LongTermExtraction extractWithModel(List<AgentExecutionMemory> records) {
        String prompt = """
                请从下面的 Agent 执行记录中提炼可以长期保存的记忆。
                你必须同时决定合适的 topic 文件名和记忆内容。

                输出必须是严格 JSON，不要输出 Markdown 代码块，不要输出额外解释。
                JSON schema:
                {
                  "targetPath": "topics/kebab-case-topic.md",
                  "description": "一句话说明这个 topic 保存什么",
                  "keywords": "逗号分隔关键词",
                  "content": "Markdown 格式的长期记忆内容"
                }

                规则:
                - targetPath 必须在 topics/ 下，文件名使用英文小写 kebab-case，以 .md 结尾。
                - content 使用 Markdown，包含二级标题和要点列表。
                - 只保留可复用事实、架构决策、接口约定、排障结论、项目偏好。
                - 不要记录临时寒暄、一次性错误、无价值流水。
                - 如涉及来源，在内容里引用 executionId。

                执行记录:
                %s
                """.formatted(formatRecords(records, 1200));
        String raw = callMemoryModel("你是一个负责维护长期记忆文件的工程助手，只输出严格 JSON。", prompt, 0.2, 3000);
        JsonObject json = parseJsonObject(raw);
        return new LongTermExtraction(
                json.has("targetPath") ? json.get("targetPath").getAsString() : DEFAULT_TARGET_PATH,
                json.has("description") ? json.get("description").getAsString() : "Automatically extracted long-term memory.",
                json.has("keywords") ? json.get("keywords").getAsString() : "agent,memory,execution",
                json.has("content") ? json.get("content").getAsString() : raw
        );
    }

    private String compressWithModel(List<AgentExecutionMemory> records) {
        String prompt = """
                请把下面的旧短期记忆压缩成一段可继续用于后续对话上下文的 Markdown 摘要。

                要求:
                - 输出 Markdown，不要输出额外解释。
                - 保留关键用户目标、最终结论、已完成动作、未完成事项、重要约束。
                - 合并重复内容。
                - 删除无价值流水。
                - 控制在 800 字以内。

                旧短期记忆:
                %s
                """.formatted(formatRecords(records, 900));
        return callMemoryModel("你是一个负责压缩短期记忆的工程助手，只输出 Markdown 摘要。", prompt, 0.2, 2000).trim();
    }

    private void ensureIndexContains(String targetPath, String description, String keywords) {
        String index = safeReadContent("MEMORY.md");
        if (index.contains("`" + targetPath + "`")) {
            return;
        }
        MemoryFileRequest request = new MemoryFileRequest();
        request.setPath("MEMORY.md");
        request.setContent(index + "\n\n- `" + targetPath + "`\n"
                + "  - description: " + compact(description, 160) + "\n"
                + "  - keywords: " + compact(keywords, 160) + "\n");
        memoryService.updateFile(request);
    }

    private String safeReadContent(String path) {
        try {
            String content = memoryService.readFile(path).getContent();
            return content == null ? "" : content;
        } catch (Exception e) {
            return "";
        }
    }

    private String compact(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String formatRecords(List<AgentExecutionMemory> records, int maxFieldLength) {
        return records.stream()
                .map(record -> """
                        executionId: %s
                        sessionId: %s
                        status: %s
                        userInput: %s
                        agentOutput: %s
                        """.formatted(
                        nullToEmpty(record.getExecutionId()),
                        nullToEmpty(record.getSessionId()),
                        nullToEmpty(record.getStatus()),
                        compact(record.getUserInput(), maxFieldLength),
                        compact(record.getAgentOutput(), maxFieldLength)))
                .collect(Collectors.joining("\n---\n"));
    }

    private String callMemoryModel(String systemPrompt, String userPrompt, double temperature, int maxToken) {
        DashScopeApi dashScopeApi = chatService.createDashScopeApi();
        DashScopeChatModel chatModel = chatService.createChatModel(dashScopeApi, temperature, maxToken, 0.9);
        ReactAgent agent = ReactAgent.builder()
                .name("memory_maintenance_agent")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .build();
        try {
            return agent.call(userPrompt).getText();
        } catch (GraphRunnerException e) {
            throw new IllegalStateException("调用大模型生成记忆失败: " + e.getMessage(), e);
        }
    }

    private JsonObject parseJsonObject(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "");
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return JsonParser.parseString(cleaned).getAsJsonObject();
    }

    private String normalizeTopicPath(String targetPath) {
        if (!StringUtils.hasText(targetPath)) {
            return DEFAULT_TARGET_PATH;
        }
        String normalized = targetPath.trim().replace('\\', '/').toLowerCase();
        if (!normalized.startsWith("topics/")) {
            normalized = "topics/" + normalized;
        }
        normalized = normalized.replaceAll("[^a-z0-9_./-]", "-")
                .replaceAll("-+", "-");
        if (!normalized.endsWith(".md")) {
            normalized += ".md";
        }
        if (normalized.contains("..")) {
            return DEFAULT_TARGET_PATH;
        }
        return normalized;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record LongTermExtraction(String targetPath, String description, String keywords, String content) {
    }
}
