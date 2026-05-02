package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.example.dto.McpToolResponse;
import org.example.entity.McpServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class McpRuntimeRegistry {

    private static final Logger logger = LoggerFactory.getLogger(McpRuntimeRegistry.class);
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };

    private final AtomicReference<McpToolSnapshot> snapshot = new AtomicReference<>(McpToolSnapshot.empty());
    private final Map<Long, McpRuntimeConnection> connections = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public synchronized McpConnectResult refresh(List<McpServerConfig> enabledServers) {
        closeAll();

        List<McpRuntimeConnection> nextConnections = new ArrayList<>();
        List<McpSyncClient> clients = new ArrayList<>();
        List<McpToolResponse> toolInfos = new ArrayList<>();
        List<McpServerRuntimeResult> serverResults = new ArrayList<>();

        for (McpServerConfig server : enabledServers) {
            try {
                McpRuntimeConnection connection = connect(server);
                nextConnections.add(connection);
                clients.add(connection.client());
                toolInfos.addAll(connection.tools());
                serverResults.add(McpServerRuntimeResult.connected(server.getId(), connection.tools().size()));
            } catch (Exception e) {
                String rootCause = rootCauseMessage(e);
                logger.warn("MCP 服务连接失败, serverId: {}, serverName: {}, transportType: {}, url: {}, reason: {}",
                        server.getId(),
                        server.getServerName(),
                        server.getTransportType(),
                        maskSensitiveUrl(finalUrl(server)),
                        rootCause,
                        e);
                serverResults.add(McpServerRuntimeResult.failed(server.getId(), rootCause));
            }
        }

        for (McpRuntimeConnection connection : nextConnections) {
            connections.put(connection.serverId(), connection);
        }

        ToolCallback[] callbacks = clients.isEmpty()
                ? new ToolCallback[0]
                : SyncMcpToolCallbackProvider.builder().mcpClients(clients).build().getToolCallbacks();
        snapshot.set(new McpToolSnapshot(callbacks, List.copyOf(toolInfos), Instant.now().toEpochMilli()));
        logger.info("MCP 运行时快照已刷新, servers: {}, tools: {}", nextConnections.size(), callbacks.length);
        return new McpConnectResult(serverResults);
    }

    public ToolCallback[] getToolCallbacks() {
        return snapshot.get().callbacks();
    }

    public List<McpToolResponse> listTools() {
        return snapshot.get().tools();
    }

    public Long refreshedAt() {
        return snapshot.get().refreshedAt();
    }

    public synchronized void closeAll() {
        for (McpRuntimeConnection connection : connections.values()) {
            try {
                connection.client().closeGracefully();
            } catch (Exception e) {
                logger.warn("关闭 MCP 客户端失败, serverId: {}", connection.serverId(), e);
            }
        }
        connections.clear();
        snapshot.set(McpToolSnapshot.empty());
    }

    private McpRuntimeConnection connect(McpServerConfig server) {
        Duration timeout = Duration.ofSeconds(resolveRequestTimeoutSeconds(server));
        logger.info("开始连接 MCP 服务, serverId: {}, serverName: {}, transportType: {}, url: {}, timeout: {}s",
                server.getId(),
                server.getServerName(),
                server.getTransportType(),
                maskSensitiveUrl(finalUrl(server)),
                timeout.toSeconds());
        McpClientTransport transport = buildTransport(server);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
        client.initialize();

        McpSchema.ListToolsResult result = client.listTools();
        List<McpToolResponse> tools = result.tools().stream()
                .map(tool -> toToolResponse(server, tool))
                .toList();
        return new McpRuntimeConnection(server.getId(), client, tools);
    }

    private McpClientTransport buildTransport(McpServerConfig server) {
        String transportType = server.getTransportType();
        Map<String, String> headers = parseHeaders(server.getHeadersJson());
        if ("SSE".equalsIgnoreCase(transportType)) {
            return HttpClientSseClientTransport.builder(server.getBaseUrl())
                    .sseEndpoint(server.getEndpoint())
                    .customizeRequest(builder -> headers.forEach(builder::header))
                    .connectTimeout(Duration.ofSeconds(Math.min(10, resolveRequestTimeoutSeconds(server))))
                    .build();
        }
        if ("STREAMABLE_HTTP".equalsIgnoreCase(transportType)) {
            return HttpClientStreamableHttpTransport.builder(server.getBaseUrl())
                    .endpoint(server.getEndpoint())
                    .resumableStreams(false)
                    .openConnectionOnStartup(false)
                    .customizeRequest(builder -> headers.forEach(builder::header))
                    .connectTimeout(Duration.ofSeconds(Math.min(10, resolveRequestTimeoutSeconds(server))))
                    .build();
        }
        throw new IllegalArgumentException("不支持的 MCP transportType: " + transportType);
    }

    private int resolveRequestTimeoutSeconds(McpServerConfig server) {
        Integer timeout = server.getRequestTimeoutSeconds();
        if (timeout == null) {
            return 30;
        }
        return Math.max(5, Math.min(timeout, 180));
    }

    private String finalUrl(McpServerConfig server) {
        String baseUrl = server.getBaseUrl() == null ? "" : server.getBaseUrl();
        String endpoint = server.getEndpoint() == null ? "" : server.getEndpoint();
        if (baseUrl.endsWith("/") && endpoint.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + endpoint;
        }
        if (!baseUrl.endsWith("/") && !endpoint.startsWith("/")) {
            return baseUrl + "/" + endpoint;
        }
        return baseUrl + endpoint;
    }

    private String maskSensitiveUrl(String url) {
        return url.replaceAll("(?i)([?&](key|token|access_token|api_key|apikey)=)[^&]+", "$1******");
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        Throwable root = throwable;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        if (root instanceof TimeoutException || containsMessage(throwable, "TimeoutException")) {
            return "初始化超时：在配置的超时时间内未收到 MCP initialize 响应";
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private boolean containsMessage(Throwable throwable, String pattern) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(pattern)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> headers = objectMapper.readValue(headersJson, STRING_MAP_TYPE);
            headers.entrySet().removeIf(entry ->
                    entry.getKey() == null
                            || entry.getKey().isBlank()
                            || entry.getValue() == null
                            || entry.getValue().isBlank());
            return headers;
        } catch (Exception e) {
            throw new IllegalArgumentException("headersJson 解析失败: " + e.getMessage(), e);
        }
    }

    private McpToolResponse toToolResponse(McpServerConfig server, McpSchema.Tool tool) {
        McpToolResponse response = new McpToolResponse();
        response.setServerId(server.getId());
        response.setServerName(server.getServerName());
        response.setToolName(tool.name());
        response.setDescription(tool.description());
        return response;
    }

    private record McpRuntimeConnection(Long serverId, McpSyncClient client, List<McpToolResponse> tools) {
    }

    private record McpToolSnapshot(ToolCallback[] callbacks, List<McpToolResponse> tools, Long refreshedAt) {
        private static McpToolSnapshot empty() {
            return new McpToolSnapshot(new ToolCallback[0], List.of(), null);
        }
    }

    public record McpConnectResult(List<McpServerRuntimeResult> servers) {
    }

    public record McpServerRuntimeResult(Long serverId, String status, String lastError, Integer toolCount) {
        private static McpServerRuntimeResult connected(Long serverId, int toolCount) {
            return new McpServerRuntimeResult(serverId, "CONNECTED", null, toolCount);
        }

        private static McpServerRuntimeResult failed(Long serverId, String lastError) {
            return new McpServerRuntimeResult(serverId, "FAILED", lastError, 0);
        }
    }
}
