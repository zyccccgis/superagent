package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.McpServerListResponse;
import org.example.dto.McpServerRequest;
import org.example.dto.McpServerResponse;
import org.example.dto.McpToolListResponse;
import org.example.dto.ToolEnabledRequest;
import org.example.service.McpServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
public class McpServerController {

    private static final Logger logger = LoggerFactory.getLogger(McpServerController.class);

    private final McpServerService mcpServerService;

    public McpServerController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @GetMapping("/servers")
    public ResponseEntity<ApiResponse<McpServerListResponse>> listServers() {
        try {
            return ResponseEntity.ok(ApiResponse.success(mcpServerService.listServers()));
        } catch (Exception e) {
            logger.error("查询 MCP 服务列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/servers")
    public ResponseEntity<ApiResponse<McpServerResponse>> createServer(@RequestBody McpServerRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(mcpServerService.createServer(request)));
        } catch (Exception e) {
            logger.error("创建 MCP 服务失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/servers/{id}")
    public ResponseEntity<ApiResponse<McpServerResponse>> updateServer(@PathVariable Long id,
                                                                       @RequestBody McpServerRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(mcpServerService.updateServer(id, request)));
        } catch (Exception e) {
            logger.error("更新 MCP 服务失败, id: {}", id, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/servers/{id}")
    public ResponseEntity<ApiResponse<String>> deleteServer(@PathVariable Long id) {
        try {
            mcpServerService.deleteServer(id);
            return ResponseEntity.ok(ApiResponse.success("deleted"));
        } catch (Exception e) {
            logger.error("删除 MCP 服务失败, id: {}", id, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/servers/{id}/enabled")
    public ResponseEntity<ApiResponse<McpServerResponse>> setEnabled(@PathVariable Long id,
                                                                     @RequestBody ToolEnabledRequest request) {
        try {
            Boolean enabled = request == null ? null : request.getEnabled();
            return ResponseEntity.ok(ApiResponse.success(mcpServerService.setEnabled(id, enabled)));
        } catch (Exception e) {
            logger.error("更新 MCP 服务开关失败, id: {}", id, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/servers/refresh")
    public ResponseEntity<ApiResponse<McpServerListResponse>> refreshRuntime() {
        try {
            return ResponseEntity.ok(ApiResponse.success(mcpServerService.refreshRuntime()));
        } catch (Exception e) {
            logger.error("刷新 MCP 运行时失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/servers/{id}/refresh")
    public ResponseEntity<ApiResponse<McpServerListResponse>> refreshServer(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success(mcpServerService.refreshRuntime()));
        } catch (Exception e) {
            logger.error("刷新 MCP 服务失败, id: {}", id, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/tools")
    public ResponseEntity<ApiResponse<McpToolListResponse>> listTools() {
        try {
            return ResponseEntity.ok(ApiResponse.success(mcpServerService.listTools()));
        } catch (Exception e) {
            logger.error("查询 MCP 工具列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
