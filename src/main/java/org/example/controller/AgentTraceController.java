package org.example.controller;

import org.example.dto.AgentTraceDetailResponse;
import org.example.dto.AgentTraceListResponse;
import org.example.dto.ApiResponse;
import org.example.service.AgentTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traces")
public class AgentTraceController {

    private static final Logger logger = LoggerFactory.getLogger(AgentTraceController.class);

    private final AgentTraceService traceService;

    public AgentTraceController(AgentTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AgentTraceListResponse>> list(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return ResponseEntity.ok(ApiResponse.success(traceService.list(sessionId, status, keyword, page, pageSize)));
        } catch (Exception e) {
            logger.error("查询 Trace 列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<ApiResponse<AgentTraceDetailResponse>> detail(@PathVariable String traceId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(traceService.detail(traceId)));
        } catch (Exception e) {
            logger.error("查询 Trace 详情失败, traceId: {}", traceId, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
