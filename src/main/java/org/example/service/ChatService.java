package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.dto.MemoryContext;
import org.example.dto.MemoryFileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${agent.safety.model-call-limit:6}")
    private int modelCallLimit;

    @Value("${agent.safety.tool-call-limit:12}")
    private int toolCallLimit;

    private final ToolSystemService toolSystemService;
    private final SkillService skillService;

    public ChatService(ToolSystemService toolSystemService, SkillService skillService) {
        this.toolSystemService = toolSystemService;
        this.skillService = skillService;
    }

    /**
     * 创建 DashScope API 实例
     */
    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    /**
     * 创建 ChatModel
     * @param temperature 控制随机性 (0.0-1.0)
     * @param maxToken 最大输出长度
     * @param topP 核采样参数
     */
    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    /**
     * 创建标准对话 ChatModel（默认参数）
     */
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    /**
     * 构建系统提示词（包含长期记忆索引、相关主题记忆和短期记忆）
     */
    public String buildSystemPrompt(MemoryContext memoryContext) {
        StringBuilder systemPromptBuilder = new StringBuilder();
        
        systemPromptBuilder.append(toolSystemService.buildToolInstructions());
        String skillsPrompt = skillService.buildSkillIndexPrompt();
        if (hasText(skillsPrompt)) {
            systemPromptBuilder.append(skillsPrompt);
        }
        
        if (memoryContext != null) {
            if (hasText(memoryContext.getMemoryIndex())) {
                systemPromptBuilder.append("--- 长期记忆索引 MEMORY.md ---\n")
                        .append(memoryContext.getMemoryIndex())
                        .append("\n--- 长期记忆索引结束 ---\n\n");
            }
            if (memoryContext.getTopicFiles() != null && !memoryContext.getTopicFiles().isEmpty()) {
                systemPromptBuilder.append("--- 已加载长期主题记忆 ---\n");
                for (MemoryFileResponse file : memoryContext.getTopicFiles()) {
                    systemPromptBuilder.append("### ").append(file.getPath()).append("\n")
                            .append(file.getContent()).append("\n\n");
                }
                systemPromptBuilder.append("--- 长期主题记忆结束 ---\n\n");
            }
            if (hasText(memoryContext.getShortMemory())) {
                systemPromptBuilder.append("--- 短期记忆和近几轮对话 ---\n")
                        .append(memoryContext.getShortMemory())
                        .append("\n--- 短期记忆结束 ---\n\n");
            }
        }
        
        systemPromptBuilder.append("请基于以上记忆上下文，回答用户的新问题。");
        
        return systemPromptBuilder.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 记录可用工具列表：mcp服务提供的工具
     */
    public void logAvailableTools() {
        toolSystemService.logAvailableTools();
    }

    /**
     * 创建 ReactAgent
     * @param chatModel 聊天模型
     * @param systemPrompt 系统提示词
     * @return 配置好的 ReactAgent
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(toolSystemService.buildLocalToolObjects())
                .tools(buildToolCallbacks())
                .hooks(createSafetyHooks())
                .build();
    }

    private ToolCallback[] buildToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        callbacks.addAll(List.of(toolSystemService.getMcpToolCallbacks()));
        callbacks.addAll(List.of(skillService.buildSkillToolCallbacks()));
        return callbacks.toArray(ToolCallback[]::new);
    }

    private List<Hook> createSafetyHooks() {
        return List.of(
                ModelCallLimitHook.builder()
                        .threadLimit(modelCallLimit)
                        .exitBehavior(ModelCallLimitHook.ExitBehavior.END)
                        .build(),
                ToolCallLimitHook.builder()
                        .threadLimit(toolCallLimit)
                        .exitBehavior(ToolCallLimitHook.ExitBehavior.END)
                        .build()
        );
    }

    /**
     * 执行 ReactAgent 对话（非流式）
     * @param agent ReactAgent 实例
     * @param question 用户问题
     * @return AI 回复
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());
        return answer;
    }
}
