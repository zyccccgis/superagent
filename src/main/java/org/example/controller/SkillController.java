package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.SkillDetailResponse;
import org.example.dto.SkillEnabledRequest;
import org.example.dto.SkillInstallRequest;
import org.example.dto.SkillListResponse;
import org.example.dto.SkillResponse;
import org.example.service.SkillService;
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
@RequestMapping("/api/skills")
public class SkillController {

    private static final Logger logger = LoggerFactory.getLogger(SkillController.class);

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SkillListResponse>> listSkills() {
        try {
            return ResponseEntity.ok(ApiResponse.success(skillService.listSkills()));
        } catch (Exception e) {
            logger.error("查询 Skills 失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{name}")
    public ResponseEntity<ApiResponse<SkillDetailResponse>> getSkill(@PathVariable String name) {
        try {
            return ResponseEntity.ok(ApiResponse.success(skillService.getSkill(name)));
        } catch (Exception e) {
            logger.error("读取 Skill 失败, name: {}", name, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/install")
    public ResponseEntity<ApiResponse<SkillResponse>> installSkill(@RequestBody SkillInstallRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(skillService.installSkill(request)));
        } catch (Exception e) {
            logger.error("安装 Skill 失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{name}/enabled")
    public ResponseEntity<ApiResponse<SkillResponse>> setEnabled(@PathVariable String name,
                                                                 @RequestBody SkillEnabledRequest request) {
        try {
            Boolean enabled = request == null ? null : request.getEnabled();
            return ResponseEntity.ok(ApiResponse.success(skillService.setEnabled(name, enabled)));
        } catch (Exception e) {
            logger.error("更新 Skill 开关失败, name: {}", name, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<ApiResponse<String>> deleteSkill(@PathVariable String name) {
        try {
            skillService.deleteSkill(name);
            return ResponseEntity.ok(ApiResponse.success("deleted"));
        } catch (Exception e) {
            logger.error("删除 Skill 失败, name: {}", name, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
