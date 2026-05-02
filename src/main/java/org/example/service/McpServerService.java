package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.McpServerListResponse;
import org.example.dto.McpServerRequest;
import org.example.dto.McpServerResponse;
import org.example.dto.McpToolListResponse;
import org.example.entity.McpServerConfig;
import org.example.mapper.McpServerConfigMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class McpServerService {

    private final McpServerConfigMapper mcpServerConfigMapper;
    private final McpRuntimeRegistry mcpRuntimeRegistry;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpServerService(McpServerConfigMapper mcpServerConfigMapper,
                            McpRuntimeRegistry mcpRuntimeRegistry,
                            JdbcTemplate jdbcTemplate) {
        this.mcpServerConfigMapper = mcpServerConfigMapper;
        this.mcpRuntimeRegistry = mcpRuntimeRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        ensureMcpServerConfigTable();
        markEnabledServersUnknown();
    }

    public McpServerListResponse listServers() {
        List<McpServerConfig> servers = mcpServerConfigMapper.selectList(new LambdaQueryWrapper<McpServerConfig>()
                .orderByAsc(McpServerConfig::getId));
        Map<Long, Integer> toolCountMap = mcpRuntimeRegistry.listTools().stream()
                .collect(Collectors.groupingBy(tool -> tool.getServerId(), Collectors.summingInt(tool -> 1)));
        List<McpServerResponse> items = servers.stream()
                .map(server -> toResponse(server, toolCountMap.getOrDefault(server.getId(), 0)))
                .toList();
        McpServerListResponse response = new McpServerListResponse();
        response.setItems(items);
        response.setTotal(items.size());
        return response;
    }

    public McpServerResponse createServer(McpServerRequest request) {
        validateRequest(request);
        McpServerConfig config = new McpServerConfig();
        applyRequest(config, request);
        config.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        config.setStatus(config.getEnabled() == 1 ? "UNKNOWN" : "DISABLED");
        mcpServerConfigMapper.insert(config);
        refreshRuntime();
        McpServerConfig saved = mcpServerConfigMapper.selectById(config.getId());
        return toResponse(saved);
    }

    public McpServerResponse updateServer(Long id, McpServerRequest request) {
        validateId(id);
        validateRequest(request);
        McpServerConfig config = requireServer(id);
        applyRequest(config, request);
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled() ? 1 : 0);
        }
        if (config.getEnabled() == 0) {
            config.setStatus("DISABLED");
            config.setLastError(null);
        } else {
            config.setStatus("UNKNOWN");
        }
        mcpServerConfigMapper.updateById(config);
        refreshRuntime();
        return toResponse(mcpServerConfigMapper.selectById(id));
    }

    public McpServerResponse setEnabled(Long id, Boolean enabled) {
        validateId(id);
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        McpServerConfig config = requireServer(id);
        config.setEnabled(enabled ? 1 : 0);
        config.setStatus(enabled ? "UNKNOWN" : "DISABLED");
        if (!enabled) {
            config.setLastError(null);
        }
        mcpServerConfigMapper.updateById(config);
        refreshRuntime();
        return toResponse(mcpServerConfigMapper.selectById(id));
    }

    public void deleteServer(Long id) {
        validateId(id);
        requireServer(id);
        mcpServerConfigMapper.deleteById(id);
        refreshRuntime();
    }

    public McpServerListResponse refreshRuntime() {
        List<McpServerConfig> enabledServers = mcpServerConfigMapper.selectList(new LambdaQueryWrapper<McpServerConfig>()
                .eq(McpServerConfig::getEnabled, 1)
                .orderByAsc(McpServerConfig::getId));
        McpRuntimeRegistry.McpConnectResult result = mcpRuntimeRegistry.refresh(enabledServers);
        LocalDateTime now = LocalDateTime.now();
        for (McpRuntimeRegistry.McpServerRuntimeResult serverResult : result.servers()) {
            McpServerConfig config = mcpServerConfigMapper.selectById(serverResult.serverId());
            if (config == null) {
                continue;
            }
            config.setStatus(serverResult.status());
            config.setLastError(serverResult.lastError());
            if ("CONNECTED".equals(serverResult.status())) {
                config.setLastConnectedAt(now);
            }
            mcpServerConfigMapper.updateById(config);
        }
        mcpServerConfigMapper.selectList(new LambdaQueryWrapper<McpServerConfig>()
                        .eq(McpServerConfig::getEnabled, 0))
                .forEach(disabled -> {
                    if (!"DISABLED".equals(disabled.getStatus())) {
                        disabled.setStatus("DISABLED");
                        mcpServerConfigMapper.updateById(disabled);
                    }
                });
        return listServers();
    }

    public McpToolListResponse listTools() {
        McpToolListResponse response = new McpToolListResponse();
        response.setItems(mcpRuntimeRegistry.listTools());
        response.setTotal(response.getItems().size());
        response.setRefreshedAt(mcpRuntimeRegistry.refreshedAt());
        return response;
    }

    private void validateRequest(McpServerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!StringUtils.hasText(request.getServerName())) {
            throw new IllegalArgumentException("serverName 不能为空");
        }
        if (!StringUtils.hasText(request.getTransportType())) {
            throw new IllegalArgumentException("transportType 不能为空");
        }
        String type = request.getTransportType().trim().toUpperCase();
        if (!"SSE".equals(type) && !"STREAMABLE_HTTP".equals(type)) {
            throw new IllegalArgumentException("transportType 仅支持 SSE 或 STREAMABLE_HTTP");
        }
        if (!StringUtils.hasText(request.getBaseUrl())) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        URI baseUri = URI.create(request.getBaseUrl().trim());
        if (baseUri.getScheme() == null
                || (!"http".equalsIgnoreCase(baseUri.getScheme()) && !"https".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl 必须是有效的 http/https 地址");
        }
        if (!StringUtils.hasText(request.getEndpoint())) {
            throw new IllegalArgumentException("endpoint 不能为空");
        }
        if (!request.getEndpoint().trim().startsWith("/")) {
            throw new IllegalArgumentException("endpoint 必须以 / 开头");
        }
        if (StringUtils.hasText(request.getHeadersJson())) {
            try {
                JsonNode root = objectMapper.readTree(request.getHeadersJson());
                if (!root.isObject()) {
                    throw new IllegalArgumentException("headersJson 必须是 JSON 对象");
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("headersJson 不是合法 JSON: " + e.getMessage());
            }
        }
        if (request.getRequestTimeoutSeconds() != null
                && (request.getRequestTimeoutSeconds() < 5 || request.getRequestTimeoutSeconds() > 180)) {
            throw new IllegalArgumentException("requestTimeoutSeconds 必须在 5 到 180 秒之间");
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id 不合法");
        }
    }

    private McpServerConfig requireServer(Long id) {
        McpServerConfig config = mcpServerConfigMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("MCP 服务不存在: " + id);
        }
        return config;
    }

    private void applyRequest(McpServerConfig config, McpServerRequest request) {
        config.setServerName(request.getServerName().trim());
        config.setTransportType(request.getTransportType().trim().toUpperCase());
        config.setBaseUrl(request.getBaseUrl().trim());
        config.setEndpoint(request.getEndpoint().trim());
        config.setHeadersJson(StringUtils.hasText(request.getHeadersJson()) ? request.getHeadersJson().trim() : null);
        config.setRequestTimeoutSeconds(request.getRequestTimeoutSeconds() == null ? 30 : request.getRequestTimeoutSeconds());
    }

    private McpServerResponse toResponse(McpServerConfig config) {
        int toolCount = (int) mcpRuntimeRegistry.listTools().stream()
                .filter(tool -> tool.getServerId().equals(config.getId()))
                .count();
        return toResponse(config, toolCount);
    }

    private McpServerResponse toResponse(McpServerConfig config, int toolCount) {
        McpServerResponse response = new McpServerResponse();
        response.setId(config.getId());
        response.setServerName(config.getServerName());
        response.setTransportType(config.getTransportType());
        response.setBaseUrl(config.getBaseUrl());
        response.setEndpoint(config.getEndpoint());
        response.setHeadersJson(config.getHeadersJson());
        response.setRequestTimeoutSeconds(config.getRequestTimeoutSeconds());
        response.setEnabled(config.getEnabled() != null && config.getEnabled() == 1);
        response.setStatus(config.getStatus());
        response.setLastError(config.getLastError());
        response.setLastConnectedAt(toEpochMillis(config.getLastConnectedAt()));
        response.setCreatedAt(toEpochMillis(config.getCreatedAt()));
        response.setUpdatedAt(toEpochMillis(config.getUpdatedAt()));
        response.setToolCount(toolCount);
        return response;
    }

    private Long toEpochMillis(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void ensureMcpServerConfigTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mcp_server_config (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                  server_name VARCHAR(64) NOT NULL COMMENT 'MCP 服务名',
                  transport_type VARCHAR(32) NOT NULL COMMENT '传输类型: SSE / STREAMABLE_HTTP',
                  base_url VARCHAR(512) NOT NULL COMMENT 'MCP 服务基础地址',
                  endpoint VARCHAR(256) NOT NULL COMMENT 'SSE 或 Streamable HTTP 端点',
                  headers_json TEXT NULL COMMENT '请求头 JSON',
                  request_timeout_seconds INT NOT NULL DEFAULT 30 COMMENT '请求超时时间秒',
                  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                  status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '运行状态',
                  last_error TEXT NULL COMMENT '最后一次连接错误',
                  last_connected_at DATETIME(3) NULL COMMENT '最后连接成功时间',
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_server_name (server_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 服务配置表'
                """);
        ensureColumn("headers_json", "ALTER TABLE mcp_server_config ADD COLUMN headers_json TEXT NULL COMMENT '请求头 JSON' AFTER endpoint");
        ensureColumn("request_timeout_seconds", "ALTER TABLE mcp_server_config ADD COLUMN request_timeout_seconds INT NOT NULL DEFAULT 30 COMMENT '请求超时时间秒' AFTER headers_json");
    }

    private void markEnabledServersUnknown() {
        mcpServerConfigMapper.selectList(new LambdaQueryWrapper<McpServerConfig>()
                        .eq(McpServerConfig::getEnabled, 1))
                .forEach(config -> {
                    config.setStatus("UNKNOWN");
                    config.setLastError(null);
                    mcpServerConfigMapper.updateById(config);
                });
    }

    private void ensureColumn(String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'mcp_server_config'
                          AND column_name = ?
                        """,
                Integer.class,
                columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }
}
