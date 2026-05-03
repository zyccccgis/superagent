package org.example.controller;

import org.example.dto.AIOpsTaskListResponse;
import org.example.dto.AIOpsTaskResponse;
import org.example.dto.ApiResponse;
import org.example.dto.ScheduledDiagnosticTaskListResponse;
import org.example.dto.ScheduledDiagnosticTaskRequest;
import org.example.dto.ScheduledDiagnosticTaskResponse;
import org.example.service.ScheduledDiagnosticTaskService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scheduled-diagnostics")
public class ScheduledDiagnosticTaskController {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledDiagnosticTaskController.class);

    private final ScheduledDiagnosticTaskService service;

    public ScheduledDiagnosticTaskController(ScheduledDiagnosticTaskService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ScheduledDiagnosticTaskListResponse>> list(@RequestParam(defaultValue = "1") Integer page,
                                                                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.list(page, pageSize)));
        } catch (Exception e) {
            logger.error("查询定时排查任务失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduledDiagnosticTaskResponse>> get(@PathVariable String scheduleId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.get(scheduleId)));
        } catch (Exception e) {
            logger.error("查询定时排查任务详情失败, scheduleId: {}", scheduleId, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduledDiagnosticTaskResponse>> create(@RequestBody ScheduledDiagnosticTaskRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.create(request)));
        } catch (Exception e) {
            logger.error("创建定时排查任务失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduledDiagnosticTaskResponse>> update(@PathVariable String scheduleId,
                                                                               @RequestBody ScheduledDiagnosticTaskRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.update(scheduleId, request)));
        } catch (Exception e) {
            logger.error("更新定时排查任务失败, scheduleId: {}", scheduleId, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String scheduleId) {
        try {
            service.delete(scheduleId);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Exception e) {
            logger.error("删除定时排查任务失败, scheduleId: {}", scheduleId, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{scheduleId}/run")
    public ResponseEntity<ApiResponse<AIOpsTaskResponse>> runNow(@PathVariable String scheduleId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.runNow(scheduleId)));
        } catch (Exception e) {
            logger.error("立即运行定时排查任务失败, scheduleId: {}", scheduleId, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{scheduleId}/runs")
    public ResponseEntity<ApiResponse<AIOpsTaskListResponse>> listRuns(@PathVariable String scheduleId,
                                                                       @RequestParam(defaultValue = "1") Integer page,
                                                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.listRuns(scheduleId, page, pageSize)));
        } catch (Exception e) {
            logger.error("查询定时排查运行记录失败, scheduleId: {}", scheduleId, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
