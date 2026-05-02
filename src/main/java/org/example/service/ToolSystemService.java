package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.ToolEnabledRequest;
import org.example.dto.ToolListResponse;
import org.example.dto.ToolResponse;
import org.example.entity.ToolConfig;
import org.example.mapper.ToolConfigMapper;
import org.example.agent.tool.ManagedTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class ToolSystemService {

    private static final Logger logger = LoggerFactory.getLogger(ToolSystemService.class);

    private final ApplicationContext applicationContext;
    private final McpRuntimeRegistry mcpRuntimeRegistry;
    private final ToolConfigMapper toolConfigMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicReference<Set<String>> enabledToolNames = new AtomicReference<>(Set.of());

    public ToolSystemService(ApplicationContext applicationContext,
                             McpRuntimeRegistry mcpRuntimeRegistry,
                             ToolConfigMapper toolConfigMapper,
                             JdbcTemplate jdbcTemplate) {
        this.applicationContext = applicationContext;
        this.mcpRuntimeRegistry = mcpRuntimeRegistry;
        this.toolConfigMapper = toolConfigMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        ensureToolConfigTable();
        syncDefaultConfigs();
        reloadEnabledToolSnapshot();
    }

    public ToolListResponse listTools() {
        Map<String, ToolConfig> configMap = loadConfigMap();
        List<ToolResponse> items = localDefinitions().values().stream()
                .map(definition -> toResponse(definition, configMap.get(definition.toolName())))
                .toList();
        ToolListResponse response = new ToolListResponse();
        response.setItems(items);
        response.setTotal(items.size());
        return response;
    }

    public ToolResponse setToolEnabled(String toolName, ToolEnabledRequest request) {
        if (!StringUtils.hasText(toolName)) {
            throw new IllegalArgumentException("toolName 不能为空");
        }
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        ToolDefinition definition = localDefinitions().get(toolName);
        if (definition == null) {
            throw new IllegalArgumentException("未知工具: " + toolName);
        }

        ToolConfig config = toolConfigMapper.selectOne(new LambdaQueryWrapper<ToolConfig>()
                .eq(ToolConfig::getToolName, toolName)
                .last("limit 1"));
        if (config == null) {
            config = new ToolConfig();
            config.setToolName(toolName);
        }
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        if (config.getId() == null) {
            toolConfigMapper.insert(config);
        } else {
            toolConfigMapper.updateById(config);
        }
        reloadEnabledToolSnapshot();
        ToolConfig refreshed = toolConfigMapper.selectOne(new LambdaQueryWrapper<ToolConfig>()
                .eq(ToolConfig::getToolName, toolName)
                .last("limit 1"));
        return toResponse(definition, refreshed);
    }

    public Object[] buildLocalToolObjects() {
        Set<String> enabled = enabledToolNames.get();
        return localDefinitions().values().stream()
                .filter(definition -> enabled.contains(definition.toolName()))
                .map(ToolDefinition::bean)
                .toArray();
    }

    public ToolCallback[] getLocalToolCallbacks() {
        return ToolCallbacks.from(buildLocalToolObjects());
    }

    public ToolCallback[] getMcpToolCallbacks() {
        return mcpRuntimeRegistry.getToolCallbacks();
    }

    public String buildToolInstructions() {
        Set<String> enabled = enabledToolNames.get();
        StringBuilder builder = new StringBuilder();
        builder.append("你可以根据用户问题调用已启用工具。当前本地工具如下：\n");
        localDefinitions().values().stream()
                .filter(definition -> enabled.contains(definition.toolName()))
                .forEach(definition -> builder
                        .append("- ")
                        .append(definition.displayName())
                        .append(": ")
                        .append(StringUtils.hasText(definition.instruction())
                                ? definition.instruction()
                                : definition.description())
                        .append(" 可用方法: ")
                        .append(String.join(", ", definition.methodNames()))
                        .append('\n'));
        if (getMcpToolCallbacks().length > 0) {
            builder.append("当用户问题更适合外部 MCP 工具时，可以调用已接入的 MCP 工具。\n");
        }
        return builder.append('\n').toString();
    }

    public void logAvailableTools() {
        logger.info("本地工具列表:");
        for (Object tool : buildLocalToolObjects()) {
            logger.info(">>> {}", tool.getClass().getSimpleName());
        }

        ToolCallback[] callbacks = getMcpToolCallbacks();
        if (callbacks.length == 0) {
            logger.info("MCP 工具列表: 当前未接入 MCP 工具");
            return;
        }
        logger.info("MCP 工具列表:");
        for (ToolCallback callback : callbacks) {
            logger.info(">>> {}", callback.getToolDefinition().name());
        }
    }

    private void syncDefaultConfigs() {
        for (ToolDefinition definition : localDefinitions().values()) {
            ToolConfig existing = toolConfigMapper.selectOne(new LambdaQueryWrapper<ToolConfig>()
                    .eq(ToolConfig::getToolName, definition.toolName())
                    .last("limit 1"));
            if (existing == null) {
                ToolConfig config = new ToolConfig();
                config.setToolName(definition.toolName());
                config.setEnabled(definition.defaultEnabled() ? 1 : 0);
                toolConfigMapper.insert(config);
            }
        }
    }

    private void ensureToolConfigTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tool_config (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                  tool_name VARCHAR(64) NOT NULL COMMENT '工具名',
                  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_tool_name (tool_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具开关配置表'
                """);
    }

    private void reloadEnabledToolSnapshot() {
        Map<String, ToolConfig> configMap = loadConfigMap();
        Set<String> enabled = localDefinitions().values().stream()
                .filter(definition -> {
                    ToolConfig config = configMap.get(definition.toolName());
                    return config == null ? definition.defaultEnabled() : config.getEnabled() != null && config.getEnabled() == 1;
                })
                .map(ToolDefinition::toolName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        enabledToolNames.set(enabled);
        logger.info("工具开关快照已刷新: {}", enabled);
    }

    private Map<String, ToolConfig> loadConfigMap() {
        List<ToolConfig> configs = toolConfigMapper.selectList(null);
        Map<String, ToolConfig> configMap = new LinkedHashMap<>();
        for (ToolConfig config : configs) {
            configMap.put(config.getToolName(), config);
        }
        return configMap;
    }

    private Map<String, ToolDefinition> localDefinitions() {
        Map<String, Object> toolBeans = applicationContext.getBeansWithAnnotation(ManagedTool.class);
        List<ToolDefinition> sortedDefinitions = toolBeans.values().stream()
                .map(this::toDefinition)
                .sorted(Comparator
                        .comparingInt(ToolDefinition::order)
                        .thenComparing(ToolDefinition::toolName))
                .toList();

        Map<String, ToolDefinition> definitions = new LinkedHashMap<>();
        for (ToolDefinition definition : sortedDefinitions) {
            ToolDefinition previous = definitions.put(definition.toolName(), definition);
            if (previous != null) {
                throw new IllegalStateException("重复的工具名: " + definition.toolName());
            }
        }
        return definitions;
    }

    private ToolDefinition toDefinition(Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        ManagedTool managedTool = targetClass.getAnnotation(ManagedTool.class);
        if (managedTool == null) {
            throw new IllegalStateException("工具缺少 ManagedTool 注解: " + targetClass.getName());
        }
        List<String> methodNames = List.of(targetClass.getMethods()).stream()
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(Method::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (methodNames.isEmpty()) {
            throw new IllegalStateException("工具没有 @Tool 方法: " + targetClass.getName());
        }
        return new ToolDefinition(
                managedTool.name(),
                managedTool.displayName(),
                managedTool.description(),
                targetClass.getSimpleName(),
                managedTool.riskLevel(),
                managedTool.instruction(),
                true,
                managedTool.defaultEnabled(),
                managedTool.order(),
                methodNames,
                bean);
    }

    private ToolResponse toResponse(ToolDefinition definition, ToolConfig config) {
        boolean enabled = config == null ? definition.defaultEnabled() : config.getEnabled() != null && config.getEnabled() == 1;
        ToolResponse response = new ToolResponse();
        response.setToolName(definition.toolName());
        response.setDisplayName(definition.displayName());
        response.setDescription(definition.description());
        response.setSourceClass(definition.sourceClass());
        response.setRiskLevel(definition.riskLevel());
        response.setEnabled(enabled);
        response.setAvailable(definition.available());
        response.setUpdatedAt(config == null || config.getUpdatedAt() == null
                ? null
                : config.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return response;
    }

    private record ToolDefinition(String toolName,
                                  String displayName,
                                  String description,
                                  String sourceClass,
                                  String riskLevel,
                                  String instruction,
                                  boolean available,
                                  boolean defaultEnabled,
                                  int order,
                                  List<String> methodNames,
                                  Object bean) {
    }
}
