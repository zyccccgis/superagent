package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.ToolEnabledRequest;
import org.example.dto.ToolListResponse;
import org.example.dto.ToolResponse;
import org.example.service.ToolSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private static final Logger logger = LoggerFactory.getLogger(ToolController.class);

    private final ToolSystemService toolSystemService;

    public ToolController(ToolSystemService toolSystemService) {
        this.toolSystemService = toolSystemService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ToolListResponse>> listTools() {
        try {
            return ResponseEntity.ok(ApiResponse.success(toolSystemService.listTools()));
        } catch (Exception e) {
            logger.error("查询工具列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{toolName}/enabled")
    public ResponseEntity<ApiResponse<ToolResponse>> setToolEnabled(@PathVariable String toolName,
                                                                    @RequestBody ToolEnabledRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(toolSystemService.setToolEnabled(toolName, request)));
        } catch (Exception e) {
            logger.error("更新工具开关失败, toolName: {}", toolName, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
