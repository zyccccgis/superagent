package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dto.AIOpsRequest;
import org.example.dto.AIOpsTaskListResponse;
import org.example.dto.AIOpsTaskResponse;
import org.example.dto.ScheduledDiagnosticTaskListResponse;
import org.example.dto.ScheduledDiagnosticTaskRequest;
import org.example.dto.ScheduledDiagnosticTaskResponse;
import org.example.entity.DiagnosticTaskStatus;
import org.example.entity.ScheduledDiagnosticTask;
import org.example.mapper.ScheduledDiagnosticTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ScheduledDiagnosticTaskService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledDiagnosticTaskService.class);
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final String TRIGGER_SOURCE = "SCHEDULED";

    private final ScheduledDiagnosticTaskMapper scheduleMapper;
    private final AIOpsTaskService aiOpsTaskService;
    private final JdbcTemplate jdbcTemplate;

    public ScheduledDiagnosticTaskService(ScheduledDiagnosticTaskMapper scheduleMapper,
                                          AIOpsTaskService aiOpsTaskService,
                                          JdbcTemplate jdbcTemplate) {
        this.scheduleMapper = scheduleMapper;
        this.aiOpsTaskService = aiOpsTaskService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS scheduled_diagnostic_task (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  schedule_id VARCHAR(64) NOT NULL,
                  task_name VARCHAR(128) NOT NULL,
                  enabled TINYINT(1) NOT NULL DEFAULT 1,
                  user_question TEXT NOT NULL,
                  prompt_template LONGTEXT DEFAULT NULL,
                  interval_minutes INT NOT NULL DEFAULT 60,
                  next_run_at DATETIME(3) NOT NULL,
                  last_run_at DATETIME(3) DEFAULT NULL,
                  last_task_id VARCHAR(64) DEFAULT NULL,
                  last_status VARCHAR(32) DEFAULT NULL,
                  last_result_summary TEXT DEFAULT NULL,
                  created_by VARCHAR(64) DEFAULT NULL,
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                  deleted TINYINT(1) NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_schedule_id (schedule_id),
                  KEY idx_enabled_next_run_at (enabled, next_run_at),
                  KEY idx_updated_at (updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    public ScheduledDiagnosticTaskListResponse list(Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<ScheduledDiagnosticTask> result = scheduleMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<ScheduledDiagnosticTask>().orderByDesc(ScheduledDiagnosticTask::getUpdatedAt));
        ScheduledDiagnosticTaskListResponse response = new ScheduledDiagnosticTaskListResponse();
        response.setItems(result.getRecords().stream().map(ScheduledDiagnosticTaskResponse::fromEntity).toList());
        response.setTotal(result.getTotal());
        return response;
    }

    public ScheduledDiagnosticTaskResponse get(String scheduleId) {
        return ScheduledDiagnosticTaskResponse.fromEntity(requireSchedule(scheduleId));
    }

    public ScheduledDiagnosticTaskResponse create(ScheduledDiagnosticTaskRequest request) {
        ScheduledDiagnosticTask task = new ScheduledDiagnosticTask();
        task.setScheduleId("sched_" + UUID.randomUUID().toString().replace("-", ""));
        applyRequest(task, request == null ? new ScheduledDiagnosticTaskRequest() : request, true);
        scheduleMapper.insert(task);
        return ScheduledDiagnosticTaskResponse.fromEntity(task);
    }

    public ScheduledDiagnosticTaskResponse update(String scheduleId, ScheduledDiagnosticTaskRequest request) {
        ScheduledDiagnosticTask task = requireSchedule(scheduleId);
        applyRequest(task, request == null ? new ScheduledDiagnosticTaskRequest() : request, false);
        scheduleMapper.updateById(task);
        return ScheduledDiagnosticTaskResponse.fromEntity(task);
    }

    public void delete(String scheduleId) {
        ScheduledDiagnosticTask task = requireSchedule(scheduleId);
        scheduleMapper.deleteById(task.getId());
    }

    public AIOpsTaskResponse runNow(String scheduleId) {
        ScheduledDiagnosticTask task = requireSchedule(scheduleId);
        return trigger(task, "MANUAL_RUN");
    }

    public AIOpsTaskListResponse listRuns(String scheduleId, Integer page, Integer pageSize) {
        requireSchedule(scheduleId);
        return aiOpsTaskService.listTasks(TRIGGER_SOURCE, scheduleId, page, pageSize);
    }

    @Scheduled(fixedDelayString = "${scheduled-diagnostics.scan-delay-ms:30000}")
    public void scanDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        var dueTasks = scheduleMapper.selectList(new LambdaQueryWrapper<ScheduledDiagnosticTask>()
                .eq(ScheduledDiagnosticTask::getEnabled, 1)
                .le(ScheduledDiagnosticTask::getNextRunAt, now)
                .orderByAsc(ScheduledDiagnosticTask::getNextRunAt)
                .last("limit 10"));
        for (ScheduledDiagnosticTask task : dueTasks) {
            try {
                if (isLastRunStillRunning(task)) {
                    continue;
                }
                trigger(task, TRIGGER_SOURCE);
            } catch (Exception e) {
                logger.error("定时排查任务触发失败, scheduleId: {}", task.getScheduleId(), e);
                task.setLastStatus(DiagnosticTaskStatus.FAILED.name());
                task.setLastResultSummary(e.getMessage());
                task.setNextRunAt(now.plusMinutes(Math.max(1, task.getIntervalMinutes() == null ? 60 : task.getIntervalMinutes())));
                scheduleMapper.updateById(task);
            }
        }
    }

    private AIOpsTaskResponse trigger(ScheduledDiagnosticTask task, String triggerLabel) {
        String prompt = buildPrompt(task);
        AIOpsRequest request = new AIOpsRequest();
        request.setUserRequest(prompt);
        request.setSessionId("schedule_" + task.getScheduleId());
        request.setTriggerSource(TRIGGER_SOURCE);
        request.setCreatedBy(task.getScheduleId());
        AIOpsTaskResponse response = aiOpsTaskService.createTask(request);

        LocalDateTime now = LocalDateTime.now();
        task.setLastRunAt(now);
        task.setLastTaskId(response.getTaskId());
        task.setLastStatus(response.getStatus());
        task.setLastResultSummary("已触发 " + triggerLabel + " 任务: " + response.getTaskId());
        task.setNextRunAt(now.plusMinutes(Math.max(1, task.getIntervalMinutes() == null ? 60 : task.getIntervalMinutes())));
        scheduleMapper.updateById(task);
        return response;
    }

    private boolean isLastRunStillRunning(ScheduledDiagnosticTask task) {
        if (!StringUtils.hasText(task.getLastTaskId()) || !DiagnosticTaskStatus.RUNNING.name().equals(task.getLastStatus())) {
            return false;
        }
        try {
            AIOpsTaskResponse response = aiOpsTaskService.getTask(task.getLastTaskId());
            if (DiagnosticTaskStatus.RUNNING.name().equals(response.getStatus())) {
                return true;
            }
            task.setLastStatus(response.getStatus());
            task.setLastResultSummary(summaryOf(response));
            scheduleMapper.updateById(task);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildPrompt(ScheduledDiagnosticTask task) {
        String template = StringUtils.hasText(task.getPromptTemplate()) ? task.getPromptTemplate().trim() : "请执行一次定时排查任务，并输出 Plan-Execute 风格的诊断报告。";
        String question = StringUtils.hasText(task.getUserQuestion()) ? task.getUserQuestion().trim() : "请检查当前系统是否存在异常告警、日志错误或性能风险。";
        return template
                + "\n\n## 本次定时排查问题\n"
                + question
                + "\n\n## 输出要求\n"
                + "先给出排查计划，再说明已执行动作、证据、结论和后续建议。";
    }

    private void applyRequest(ScheduledDiagnosticTask task, ScheduledDiagnosticTaskRequest request, boolean create) {
        task.setTaskName(defaultText(request.getTaskName(), create ? "定时排查任务" : task.getTaskName()));
        if (request.getEnabled() != null || create) {
            task.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        }
        task.setUserQuestion(defaultText(request.getUserQuestion(), create ? "请检查当前系统是否存在异常告警、日志错误或性能风险。" : task.getUserQuestion()));
        task.setPromptTemplate(defaultText(request.getPromptTemplate(), create ? "你是企业级 SRE，请使用 Plan-Execute 方式执行定时排查。" : task.getPromptTemplate()));
        Integer currentInterval = task.getIntervalMinutes() == null ? 60 : task.getIntervalMinutes();
        int interval = request.getIntervalMinutes() == null ? (create ? 60 : currentInterval) : request.getIntervalMinutes();
        task.setIntervalMinutes(Math.max(1, interval));
        task.setNextRunAt(parseNextRunAt(request.getNextRunAt(), create ? LocalDateTime.now().plusMinutes(task.getIntervalMinutes()) : task.getNextRunAt()));
        task.setCreatedBy(defaultText(request.getCreatedBy(), create ? "system" : task.getCreatedBy()));
    }

    private LocalDateTime parseNextRunAt(String value, LocalDateTime fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback == null ? LocalDateTime.now().plusHours(1) : fallback;
        }
        try {
            return LocalDateTime.parse(value.trim(), INPUT_FORMATTER);
        } catch (Exception ignored) {
            return LocalDateTime.parse(value.trim());
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String summaryOf(AIOpsTaskResponse response) {
        if (DiagnosticTaskStatus.SUCCESS.name().equals(response.getStatus())) {
            String report = response.getFinalReport();
            return StringUtils.hasText(report) ? (report.length() > 500 ? report.substring(0, 500) : report) : "任务成功";
        }
        return StringUtils.hasText(response.getErrorMessage()) ? response.getErrorMessage() : response.getStatus();
    }

    private ScheduledDiagnosticTask requireSchedule(String scheduleId) {
        ScheduledDiagnosticTask task = scheduleMapper.selectOne(new LambdaQueryWrapper<ScheduledDiagnosticTask>()
                .eq(ScheduledDiagnosticTask::getScheduleId, scheduleId)
                .last("limit 1"));
        if (task == null) {
            throw new NoSuchElementException("定时排查任务不存在: " + scheduleId);
        }
        return task;
    }
}
