package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.AgentExecutionMemoryListResponse;
import org.example.dto.AgentExecutionMemoryRequest;
import org.example.dto.AgentExecutionMemoryResponse;
import org.example.dto.MemoryCompressRequest;
import org.example.dto.MemoryCompressResponse;
import org.example.dto.MemoryExtractRequest;
import org.example.dto.MemoryExtractResponse;
import org.example.dto.MemoryFileListResponse;
import org.example.dto.MemoryFileRequest;
import org.example.dto.MemoryFileResponse;
import org.example.service.AgentExecutionMemoryService;
import org.example.service.MemoryMaintenanceService;
import org.example.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private static final Logger logger = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryService memoryService;
    private final AgentExecutionMemoryService executionMemoryService;
    private final MemoryMaintenanceService memoryMaintenanceService;

    public MemoryController(MemoryService memoryService,
                            AgentExecutionMemoryService executionMemoryService,
                            MemoryMaintenanceService memoryMaintenanceService) {
        this.memoryService = memoryService;
        this.executionMemoryService = executionMemoryService;
        this.memoryMaintenanceService = memoryMaintenanceService;
    }

    @GetMapping("/files")
    public ResponseEntity<ApiResponse<MemoryFileListResponse>> listFiles(
            @RequestParam(required = false, defaultValue = "all") String type,
            @RequestParam(required = false) String keyword) {
        try {
            return ResponseEntity.ok(ApiResponse.success(memoryService.listFiles(type, keyword)));
        } catch (Exception e) {
            logger.error("查询记忆文件列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/files/content")
    public ResponseEntity<ApiResponse<MemoryFileResponse>> readFile(@RequestParam String path) {
        try {
            return ResponseEntity.ok(ApiResponse.success(memoryService.readFile(path)));
        } catch (Exception e) {
            logger.error("读取记忆文件失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/files")
    public ResponseEntity<ApiResponse<MemoryFileResponse>> createFile(@RequestBody MemoryFileRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(memoryService.createFile(request)));
        } catch (Exception e) {
            logger.error("创建记忆文件失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/files")
    public ResponseEntity<ApiResponse<MemoryFileResponse>> updateFile(@RequestBody MemoryFileRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(memoryService.updateFile(request)));
        } catch (Exception e) {
            logger.error("更新记忆文件失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/files")
    public ResponseEntity<ApiResponse<String>> deleteFile(@RequestParam String path) {
        try {
            memoryService.deleteFile(path);
            return ResponseEntity.ok(ApiResponse.success("deleted"));
        } catch (Exception e) {
            logger.error("删除记忆文件失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/executions")
    public ResponseEntity<ApiResponse<AgentExecutionMemoryListResponse>> listExecutions(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    executionMemoryService.list(sessionId, status, keyword, page, pageSize)));
        } catch (Exception e) {
            logger.error("查询短期记忆列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/executions/detail")
    public ResponseEntity<ApiResponse<AgentExecutionMemoryResponse>> getExecution(@RequestParam String executionId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(executionMemoryService.get(executionId)));
        } catch (Exception e) {
            logger.error("读取短期记忆失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/executions")
    public ResponseEntity<ApiResponse<AgentExecutionMemoryResponse>> createExecution(
            @RequestBody AgentExecutionMemoryRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(executionMemoryService.create(request)));
        } catch (Exception e) {
            logger.error("创建短期记忆失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/executions")
    public ResponseEntity<ApiResponse<AgentExecutionMemoryResponse>> updateExecution(
            @RequestParam String executionId,
            @RequestBody AgentExecutionMemoryRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(executionMemoryService.update(executionId, request)));
        } catch (Exception e) {
            logger.error("更新短期记忆失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/executions")
    public ResponseEntity<ApiResponse<String>> deleteExecution(@RequestParam String executionId) {
        try {
            executionMemoryService.delete(executionId);
            return ResponseEntity.ok(ApiResponse.success("deleted"));
        } catch (Exception e) {
            logger.error("删除短期记忆失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/extract")
    public ResponseEntity<ApiResponse<MemoryExtractResponse>> extractLongTermMemory(
            @RequestBody(required = false) MemoryExtractRequest request) {
        try {
            logger.error("长期记忆自动抽取中");
            return ResponseEntity.ok(ApiResponse.success(memoryMaintenanceService.extractLongTermMemory(request)));
        } catch (Exception e) {
            logger.error("长期记忆自动抽取失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/compress")
    public ResponseEntity<ApiResponse<MemoryCompressResponse>> compressShortTermMemory(
            @RequestBody(required = false) MemoryCompressRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(memoryMaintenanceService.compressShortTermMemory(request)));
        } catch (Exception e) {
            logger.error("短期记忆压缩失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
