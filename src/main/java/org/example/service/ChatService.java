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
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.MySqlTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.example.agent.tool.WebSearchTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)
    private MySqlTools mySqlTools;

    @Autowired(required = false)
    private WebSearchTools webSearchTools;

    @Autowired(required = false)  // Mock 模式下才注册，所以设置为 optional,真实环境通过mcp配置注入
    private QueryLogsTools queryLogsTools;

    @Autowired(required = false)
    private ToolCallbackProvider tools;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${agent.safety.model-call-limit:6}")
    private int modelCallLimit;

    @Value("${agent.safety.tool-call-limit:12}")
    private int toolCallLimit;

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
        
        // 基础系统提示
        systemPromptBuilder.append("你是一个专业的智能助手，可以获取当前时间、查询天气信息、搜索内部文档知识库，以及查询 Prometheus 告警信息。\n");
        systemPromptBuilder.append("当用户询问时间相关问题时，使用 getCurrentDateTime 工具。\n");
        systemPromptBuilder.append("当用户需要查询公司内部文档、流程、最佳实践或技术指南时，使用 queryInternalDocs 工具。\n");
        systemPromptBuilder.append("当用户需要查询 Prometheus 告警、监控指标或系统告警状态时，使用 queryPrometheusAlerts 工具。\n");
        if (mySqlTools != null) {
            systemPromptBuilder.append("当用户需要查询或修改 MySQL 业务数据时，优先使用 MySQL 工具。查询用 executeMySqlQuery，写入用 executeMySqlUpdate。禁止执行高危 DDL。\n");
        }
        if (webSearchTools != null) {
            systemPromptBuilder.append("当用户需要访问公开网页或公开 HTTP API 时，使用 fetchWebPage 工具。禁止尝试访问 localhost、本机服务或内网地址。\n");
        }
        systemPromptBuilder.append("当用户需要查询腾讯云日志时，请调用腾讯云mcp服务查询,默认查询地域ap-guangzhou,查询时间范围为近一个月。\n\n");
        
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
     * 动态构建方法工具数组
     * 根据 cls.mock-enabled 决定是否包含 QueryLogsTools
     */
    public Object[] buildMethodToolsArray() {
        List<Object> methodTools = new java.util.ArrayList<>();
        methodTools.add(dateTimeTools);
        methodTools.add(internalDocsTools);
        methodTools.add(queryMetricsTools);
        if (mySqlTools != null) {
            methodTools.add(mySqlTools);
        }
        if (webSearchTools != null) {
            methodTools.add(webSearchTools);
        }
        if (queryLogsTools != null) {
            methodTools.add(queryLogsTools);
        }
        return methodTools.toArray();
    }

    /**
     * 获取工具回调列表，mcp服务提供的工具
     */
    public ToolCallback[] getToolCallbacks() {
        return tools == null ? new ToolCallback[0] : tools.getToolCallbacks();
    }

    /**
     * 记录可用工具列表：mcp服务提供的工具
     */
    public void logAvailableTools() {
        ToolCallback[] toolCallbacks = getToolCallbacks();
        if (toolCallbacks.length == 0) {
            logger.info("可用工具列表: 当前未接入 MCP 工具");
            return;
        }
        logger.info("可用工具列表:");
        for (ToolCallback toolCallback : toolCallbacks) {
            logger.info(">>> {}", toolCallback.getToolDefinition().name());
        }
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
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .hooks(createSafetyHooks())
                .build();
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
