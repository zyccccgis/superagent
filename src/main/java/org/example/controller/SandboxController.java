package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.SandboxRunResponse;
import org.example.dto.SandboxTestRequest;
import org.example.service.SandboxRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private static final Logger logger = LoggerFactory.getLogger(SandboxController.class);

    private final SandboxRunnerService sandboxRunnerService;

    public SandboxController(SandboxRunnerService sandboxRunnerService) {
        this.sandboxRunnerService = sandboxRunnerService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        try {
            return ResponseEntity.ok(ApiResponse.success(sandboxRunnerService.status()));
        } catch (Exception e) {
            logger.error("查询沙箱状态失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<SandboxRunResponse>> test(@RequestBody(required = false) SandboxTestRequest request) {
        try {
            String code = request != null && StringUtils.hasText(request.getCode())
                    ? request.getCode()
                    : "import os, sys\nprint('sandbox-ok')\nprint('cwd=' + os.getcwd())\nprint('args=' + ','.join(sys.argv[1:]))\n";
            SandboxRunnerService.SandboxResult result = sandboxRunnerService.runInlinePython(
                    code,
                    request == null ? null : request.getArgs());
            return ResponseEntity.ok(ApiResponse.success(SandboxRunResponse.from(result)));
        } catch (Exception e) {
            logger.error("沙箱测试失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
