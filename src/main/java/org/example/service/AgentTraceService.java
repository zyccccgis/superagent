package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dto.AgentToolCallResponse;
import org.example.dto.AgentTraceDetailResponse;
import org.example.dto.AgentTraceListResponse;
import org.example.dto.AgentTraceResponse;
import org.example.dto.AgentTraceStepResponse;
import org.example.entity.AgentToolCall;
import org.example.entity.AgentTrace;
import org.example.entity.AgentTraceStep;
import org.example.mapper.AgentToolCallMapper;
import org.example.mapper.AgentTraceMapper;
import org.example.mapper.AgentTraceStepMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class AgentTraceService {

    private static final int SUMMARY_LIMIT = 4000;

    private final AgentTraceMapper traceMapper;
    private final AgentTraceStepMapper stepMapper;
    private final AgentToolCallMapper toolCallMapper;
    private final JdbcTemplate jdbcTemplate;

    public AgentTraceService(AgentTraceMapper traceMapper,
                             AgentTraceStepMapper stepMapper,
                             AgentToolCallMapper toolCallMapper,
                             JdbcTemplate jdbcTemplate) {
        this.traceMapper = traceMapper;
        this.stepMapper = stepMapper;
        this.toolCallMapper = toolCallMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        ensureTables();
    }

    public String startTrace(String sessionId, String userInput, String modelName) {
        LocalDateTime now = LocalDateTime.now();
        AgentTrace trace = new AgentTrace();
        trace.setTraceId("trace_" + UUID.randomUUID().toString().replace("-", ""));
        trace.setSessionId(sessionId);
        trace.setUserInput(truncate(userInput, SUMMARY_LIMIT));
        trace.setStatus("RUNNING");
        trace.setStartTime(now);
        trace.setModelName(modelName);
        trace.setCreatedAt(now);
        trace.setUpdatedAt(now);
        traceMapper.insert(trace);
        return trace.getTraceId();
    }

    public void finishTrace(String traceId) {
        AgentTrace trace = findTrace(traceId);
        if (trace == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        trace.setStatus("SUCCESS");
        trace.setEndTime(now);
        trace.setDurationMs(duration(trace.getStartTime(), now));
        trace.setUpdatedAt(now);
        traceMapper.updateById(trace);
    }

    public void failTrace(String traceId, Throwable error) {
        failTrace(traceId, error == null ? null : error.getMessage());
    }

    public void failTrace(String traceId, String errorMessage) {
        AgentTrace trace = findTrace(traceId);
        if (trace == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        trace.setStatus("FAILED");
        trace.setEndTime(now);
        trace.setDurationMs(duration(trace.getStartTime(), now));
        trace.setErrorMessage(truncate(errorMessage, 2000));
        trace.setUpdatedAt(now);
        traceMapper.updateById(trace);
    }

    public Long startStep(String traceId, String stepType, String stepName, String inputSummary) {
        if (!StringUtils.hasText(traceId)) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        AgentTraceStep step = new AgentTraceStep();
        step.setTraceId(traceId);
        step.setStepType(stepType);
        step.setStepName(stepName);
        step.setStatus("RUNNING");
        step.setInputSummary(truncate(inputSummary, SUMMARY_LIMIT));
        step.setStartTime(now);
        step.setCreatedAt(now);
        stepMapper.insert(step);
        return step.getId();
    }

    public void finishStep(Long stepId, String outputSummary) {
        AgentTraceStep step = findStep(stepId);
        if (step == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        step.setStatus("SUCCESS");
        step.setEndTime(now);
        step.setDurationMs(duration(step.getStartTime(), now));
        step.setOutputSummary(truncate(outputSummary, SUMMARY_LIMIT));
        stepMapper.updateById(step);
    }

    public void failStep(Long stepId, Throwable error) {
        AgentTraceStep step = findStep(stepId);
        if (step == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        step.setStatus("FAILED");
        step.setEndTime(now);
        step.setDurationMs(duration(step.getStartTime(), now));
        step.setErrorMessage(truncate(error == null ? null : error.getMessage(), 2000));
        stepMapper.updateById(step);
    }

    public void recordToolCall(String traceId,
                               Long stepId,
                               String toolName,
                               String toolSource,
                               String requestJson,
                               String responseSummary,
                               String status,
                               Long durationMs,
                               String errorMessage) {
        if (!StringUtils.hasText(traceId)) {
            return;
        }
        AgentToolCall call = new AgentToolCall();
        call.setTraceId(traceId);
        call.setStepId(stepId);
        call.setToolName(toolName);
        call.setToolSource(toolSource);
        call.setRequestJson(truncate(requestJson, SUMMARY_LIMIT));
        call.setResponseSummary(truncate(responseSummary, SUMMARY_LIMIT));
        call.setStatus(status);
        call.setDurationMs(durationMs);
        call.setErrorMessage(truncate(errorMessage, 2000));
        call.setCreatedAt(LocalDateTime.now());
        toolCallMapper.insert(call);
    }

    public AgentTraceListResponse list(String sessionId, String status, String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        LambdaQueryWrapper<AgentTrace> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(sessionId)) {
            wrapper.eq(AgentTrace::getSessionId, sessionId);
        }
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            wrapper.eq(AgentTrace::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AgentTrace::getTraceId, keyword)
                    .or()
                    .like(AgentTrace::getUserInput, keyword)
                    .or()
                    .like(AgentTrace::getSessionId, keyword));
        }
        wrapper.orderByDesc(AgentTrace::getStartTime);
        Page<AgentTrace> result = traceMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        AgentTraceListResponse response = new AgentTraceListResponse();
        response.setItems(result.getRecords().stream().map(this::toTraceResponse).toList());
        response.setTotal(result.getTotal());
        response.setPage(safePage);
        response.setPageSize(safePageSize);
        return response;
    }

    public AgentTraceDetailResponse detail(String traceId) {
        AgentTrace trace = findTrace(traceId);
        if (trace == null) {
            throw new IllegalArgumentException("Trace 不存在: " + traceId);
        }
        List<AgentTraceStep> steps = stepMapper.selectList(new LambdaQueryWrapper<AgentTraceStep>()
                .eq(AgentTraceStep::getTraceId, traceId)
                .orderByAsc(AgentTraceStep::getStartTime)
                .orderByAsc(AgentTraceStep::getId));
        List<AgentToolCall> toolCalls = toolCallMapper.selectList(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getTraceId, traceId)
                .orderByAsc(AgentToolCall::getCreatedAt)
                .orderByAsc(AgentToolCall::getId));
        AgentTraceDetailResponse response = new AgentTraceDetailResponse();
        response.setTrace(toTraceResponse(trace));
        response.setSteps(steps.stream().map(this::toStepResponse).toList());
        response.setToolCalls(toolCalls.stream().map(this::toToolCallResponse).toList());
        return response;
    }

    private AgentTrace findTrace(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return null;
        }
        return traceMapper.selectOne(new LambdaQueryWrapper<AgentTrace>()
                .eq(AgentTrace::getTraceId, traceId)
                .last("limit 1"));
    }

    private AgentTraceStep findStep(Long stepId) {
        if (stepId == null) {
            return null;
        }
        return stepMapper.selectById(stepId);
    }

    private AgentTraceResponse toTraceResponse(AgentTrace trace) {
        AgentTraceResponse response = new AgentTraceResponse();
        response.setId(trace.getId());
        response.setTraceId(trace.getTraceId());
        response.setSessionId(trace.getSessionId());
        response.setUserInput(trace.getUserInput());
        response.setStatus(trace.getStatus());
        response.setStartTime(epochMillis(trace.getStartTime()));
        response.setEndTime(epochMillis(trace.getEndTime()));
        response.setDurationMs(trace.getDurationMs());
        response.setModelName(trace.getModelName());
        response.setErrorMessage(trace.getErrorMessage());
        return response;
    }

    private AgentTraceStepResponse toStepResponse(AgentTraceStep step) {
        AgentTraceStepResponse response = new AgentTraceStepResponse();
        response.setId(step.getId());
        response.setTraceId(step.getTraceId());
        response.setStepType(step.getStepType());
        response.setStepName(step.getStepName());
        response.setStatus(step.getStatus());
        response.setInputSummary(step.getInputSummary());
        response.setOutputSummary(step.getOutputSummary());
        response.setStartTime(epochMillis(step.getStartTime()));
        response.setEndTime(epochMillis(step.getEndTime()));
        response.setDurationMs(step.getDurationMs());
        response.setErrorMessage(step.getErrorMessage());
        return response;
    }

    private AgentToolCallResponse toToolCallResponse(AgentToolCall call) {
        AgentToolCallResponse response = new AgentToolCallResponse();
        response.setId(call.getId());
        response.setTraceId(call.getTraceId());
        response.setStepId(call.getStepId());
        response.setToolName(call.getToolName());
        response.setToolSource(call.getToolSource());
        response.setRequestJson(call.getRequestJson());
        response.setResponseSummary(call.getResponseSummary());
        response.setStatus(call.getStatus());
        response.setDurationMs(call.getDurationMs());
        response.setErrorMessage(call.getErrorMessage());
        response.setCreatedAt(epochMillis(call.getCreatedAt()));
        return response;
    }

    private Long duration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).toMillis();
    }

    private Long epochMillis(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...[truncated]";
    }

    private void ensureTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS agent_trace (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                  trace_id VARCHAR(80) NOT NULL COMMENT 'Trace ID',
                  session_id VARCHAR(128) NULL COMMENT '会话 ID',
                  user_input TEXT NULL COMMENT '用户输入',
                  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '状态',
                  start_time DATETIME(3) NOT NULL COMMENT '开始时间',
                  end_time DATETIME(3) NULL COMMENT '结束时间',
                  duration_ms BIGINT NULL COMMENT '耗时毫秒',
                  model_name VARCHAR(128) NULL COMMENT '模型名称',
                  error_message TEXT NULL COMMENT '错误信息',
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_trace_id (trace_id),
                  KEY idx_session_time (session_id, start_time),
                  KEY idx_status_time (status, start_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 执行 Trace 表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS agent_trace_step (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                  trace_id VARCHAR(80) NOT NULL COMMENT 'Trace ID',
                  step_type VARCHAR(64) NOT NULL COMMENT '步骤类型',
                  step_name VARCHAR(128) NOT NULL COMMENT '步骤名称',
                  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '状态',
                  input_summary MEDIUMTEXT NULL COMMENT '输入摘要',
                  output_summary MEDIUMTEXT NULL COMMENT '输出摘要',
                  start_time DATETIME(3) NOT NULL COMMENT '开始时间',
                  end_time DATETIME(3) NULL COMMENT '结束时间',
                  duration_ms BIGINT NULL COMMENT '耗时毫秒',
                  error_message TEXT NULL COMMENT '错误信息',
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                  PRIMARY KEY (id),
                  KEY idx_trace_time (trace_id, start_time),
                  KEY idx_step_type (step_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent Trace 步骤表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS agent_tool_call (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                  trace_id VARCHAR(80) NOT NULL COMMENT 'Trace ID',
                  step_id BIGINT NULL COMMENT '步骤 ID',
                  tool_name VARCHAR(128) NOT NULL COMMENT '工具名',
                  tool_source VARCHAR(32) NOT NULL COMMENT '工具来源',
                  request_json MEDIUMTEXT NULL COMMENT '请求参数',
                  response_summary MEDIUMTEXT NULL COMMENT '响应摘要',
                  status VARCHAR(32) NOT NULL COMMENT '状态',
                  duration_ms BIGINT NULL COMMENT '耗时毫秒',
                  error_message TEXT NULL COMMENT '错误信息',
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                  PRIMARY KEY (id),
                  KEY idx_trace_id (trace_id),
                  KEY idx_step_id (step_id),
                  KEY idx_tool_name (tool_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 工具调用表'
                """);
    }
}
