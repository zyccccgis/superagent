package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dto.AgentExecutionMemoryListResponse;
import org.example.dto.AgentExecutionMemoryRequest;
import org.example.dto.AgentExecutionMemoryResponse;
import org.example.dto.MemoryCompressRequest;
import org.example.dto.MemoryContext;
import org.example.dto.MemoryExtractRequest;
import org.example.entity.AgentExecutionMemory;
import org.example.mapper.AgentExecutionMemoryMapper;
import org.example.service.AgentExecutionMemoryService;
import org.example.service.MemoryMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MySqlAgentExecutionMemoryService implements AgentExecutionMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(MySqlAgentExecutionMemoryService.class);

    private final AgentExecutionMemoryMapper memoryMapper;
    private final AsyncTaskExecutor chatTaskExecutor;
    private final MemoryMaintenanceService memoryMaintenanceService;
    private final boolean autoCompressionEnabled;
    private final int autoCompressionThreshold;
    private final int autoCompressionKeepRecent;
    private final int autoCompressionMaxRecords;
    private final boolean autoExtractionEnabled;
    private final int autoExtractionSuccessThreshold;

    public MySqlAgentExecutionMemoryService(AgentExecutionMemoryMapper memoryMapper,
                                            AsyncTaskExecutor chatTaskExecutor,
                                            @Lazy
                                            MemoryMaintenanceService memoryMaintenanceService,
                                            @Value("${memory.short-compression.enabled:true}") boolean autoCompressionEnabled,
                                            @Value("${memory.short-compression.threshold:12}") int autoCompressionThreshold,
                                            @Value("${memory.short-compression.keep-recent:6}") int autoCompressionKeepRecent,
                                            @Value("${memory.short-compression.max-records:30}") int autoCompressionMaxRecords,
                                            @Value("${memory.long-extraction.enabled:true}") boolean autoExtractionEnabled,
                                            @Value("${memory.long-extraction.success-threshold:5}") int autoExtractionSuccessThreshold) {
        this.memoryMapper = memoryMapper;
        this.chatTaskExecutor = chatTaskExecutor;
        this.memoryMaintenanceService = memoryMaintenanceService;
        this.autoCompressionEnabled = autoCompressionEnabled;
        this.autoCompressionThreshold = Math.max(2, autoCompressionThreshold);
        this.autoCompressionKeepRecent = Math.max(1, Math.min(autoCompressionKeepRecent, this.autoCompressionThreshold - 1));
        this.autoCompressionMaxRecords = Math.max(2, autoCompressionMaxRecords);
        this.autoExtractionEnabled = autoExtractionEnabled;
        this.autoExtractionSuccessThreshold = Math.max(1, autoExtractionSuccessThreshold);
    }

    @Override
    public void recordAsync(String sessionId,
                            String userInput,
                            String agentOutput,
                            MemoryContext memoryContext,
                            String status,
                            String errorMessage,
                            long startedAtMillis) {
        chatTaskExecutor.execute(() -> record(sessionId, userInput, agentOutput, memoryContext, status, errorMessage, startedAtMillis));
    }

    private void record(String sessionId,
                        String userInput,
                        String agentOutput,
                        MemoryContext memoryContext,
                        String status,
                        String errorMessage,
                        long startedAtMillis) {
        try {
            long finishedAtMillis = System.currentTimeMillis();
            AgentExecutionMemory memory = new AgentExecutionMemory();
            memory.setExecutionId("exec_" + UUID.randomUUID().toString().replace("-", ""));
            memory.setSessionId(sessionId);
            memory.setUserInput(userInput);
            memory.setAgentOutput(agentOutput);
            memory.setMemoryIndexSnapshot(memoryContext == null ? null : memoryContext.getMemoryIndex());
            memory.setShortMemorySnapshot(memoryContext == null ? null : memoryContext.getShortMemory());
            memory.setStatus(status);
            memory.setErrorMessage(errorMessage);
            memory.setStartedAt(toLocalDateTime(startedAtMillis));
            memory.setFinishedAt(toLocalDateTime(finishedAtMillis));
            memory.setDurationMs(finishedAtMillis - startedAtMillis);
            memory.setCreatedAt(LocalDateTime.now());
            memory.setUpdatedAt(LocalDateTime.now());
            memory.setDeleted(0);
            if (memoryContext != null && memoryContext.getTopicFiles() != null && !memoryContext.getTopicFiles().isEmpty()) {
                String topicSnapshot = memoryContext.getTopicFiles().stream()
                        .map(file -> "## " + file.getPath() + "\n" + nullToEmpty(file.getContent()))
                        .collect(Collectors.joining("\n\n"));
                memory.setMemoryIndexSnapshot(nullToEmpty(memory.getMemoryIndexSnapshot()) + "\n\n--- Loaded Topics ---\n" + topicSnapshot);
            }
            memoryMapper.insert(memory);
            extractLongTermMemoryIfNeeded(memory);
            compressOldMemoriesIfNeeded(sessionId);
        } catch (Exception e) {
            logger.warn("异步记录 Agent 执行记忆失败: {}", e.getMessage(), e);
        }
    }

    private void extractLongTermMemoryIfNeeded(AgentExecutionMemory memory) {
        if (!autoExtractionEnabled || memory == null || !"SUCCESS".equals(memory.getStatus())) {
            return;
        }
        boolean thresholdReached = isSuccessThresholdReached(memory.getSessionId());
        try {
            MemoryExtractRequest request = new MemoryExtractRequest();
            request.setSessionId(memory.getSessionId());
            if (thresholdReached) {
                request.setLimit(autoExtractionSuccessThreshold);
            } else {
                request.setExecutionId(memory.getExecutionId());
                request.setLimit(1);
            }
            var response = memoryMaintenanceService.extractLongTermMemory(request);
            logger.info("长期记忆自动抽取完成, sessionId: {}, executionId: {}, trigger: {}, extractedCount: {}, targetPath: {}",
                    memory.getSessionId(),
                    memory.getExecutionId(),
                    thresholdReached ? "threshold" : "single-turn",
                    response.getExtractedCount(),
                    response.getTargetPath());
        } catch (Exception e) {
            logger.warn("长期记忆自动抽取失败, sessionId: {}, executionId: {}, reason: {}",
                    memory.getSessionId(), memory.getExecutionId(), e.getMessage(), e);
        }
    }

    private boolean isSuccessThresholdReached(String sessionId) {
        if (!hasText(sessionId)) {
            return false;
        }
        Long successCount = memoryMapper.selectCount(new LambdaQueryWrapper<AgentExecutionMemory>()
                .eq(AgentExecutionMemory::getSessionId, sessionId)
                .eq(AgentExecutionMemory::getStatus, "SUCCESS"));
        long count = successCount == null ? 0 : successCount;
        return count > 0 && count % autoExtractionSuccessThreshold == 0;
    }

    private void compressOldMemoriesIfNeeded(String sessionId) {
        if (!autoCompressionEnabled || !hasText(sessionId)) {
            return;
        }
        Long activeCount = memoryMapper.selectCount(new LambdaQueryWrapper<AgentExecutionMemory>()
                .eq(AgentExecutionMemory::getSessionId, sessionId)
                .ne(AgentExecutionMemory::getStatus, "COMPRESSED"));
        long count = activeCount == null ? 0 : activeCount;
        if (count <= autoCompressionThreshold) {
            return;
        }
        try {
            MemoryCompressRequest request = new MemoryCompressRequest();
            request.setSessionId(sessionId);
            request.setKeepRecent(autoCompressionKeepRecent);
            request.setMaxRecords(Math.min(autoCompressionMaxRecords, (int) count));
            memoryMaintenanceService.compressShortTermMemory(request);
            logger.info("短期记忆已自动压缩, sessionId: {}, activeCount: {}, threshold: {}",
                    sessionId, count, autoCompressionThreshold);
        } catch (Exception e) {
            logger.warn("短期记忆自动压缩失败, sessionId: {}, reason: {}", sessionId, e.getMessage(), e);
        }
    }

    @Override
    public AgentExecutionMemoryListResponse list(String sessionId,
                                                 String status,
                                                 String keyword,
                                                 Integer page,
                                                 Integer pageSize) {
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        LambdaQueryWrapper<AgentExecutionMemory> pageWrapper = buildQueryWrapper(sessionId, status, keyword)
                .orderByDesc(AgentExecutionMemory::getCreatedAt);
        Page<AgentExecutionMemory> pageResult = memoryMapper.selectPage(new Page<>(resolvedPage, resolvedPageSize), pageWrapper);

        AgentExecutionMemoryListResponse response = new AgentExecutionMemoryListResponse();
        response.setItems(pageResult.getRecords().stream()
                .map(AgentExecutionMemoryResponse::fromEntity)
                .toList());
        response.setTotal(pageResult.getTotal());
        response.setPage(resolvedPage);
        response.setPageSize(resolvedPageSize);
        return response;
    }

    @Override
    public AgentExecutionMemoryResponse get(String executionId) {
        return AgentExecutionMemoryResponse.fromEntity(getEntity(executionId));
    }

    @Override
    public AgentExecutionMemoryResponse update(String executionId, AgentExecutionMemoryRequest request) {
        AgentExecutionMemory entity = getEntity(executionId);
        applyRequest(entity, request);
        entity.setUpdatedAt(LocalDateTime.now());
        memoryMapper.updateById(entity);
        return AgentExecutionMemoryResponse.fromEntity(entity);
    }

    @Override
    public void delete(String executionId) {
        AgentExecutionMemory entity = getEntity(executionId);
        memoryMapper.deleteById(entity.getId());
    }

    @Override
    public String recentSnapshot(String sessionId, int limit) {
        if (!hasText(sessionId)) {
            return "";
        }
        int resolvedLimit = Math.max(1, Math.min(limit, 20));
        LambdaQueryWrapper<AgentExecutionMemory> wrapper = new LambdaQueryWrapper<AgentExecutionMemory>()
                .eq(AgentExecutionMemory::getSessionId, sessionId)
                .orderByDesc(AgentExecutionMemory::getCreatedAt)
                .last("limit " + resolvedLimit);
        List<AgentExecutionMemory> records = memoryMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return "";
        }
        java.util.Collections.reverse(records);
        return records.stream()
                .map(record -> "## " + nullToEmpty(record.getExecutionId()) + "\n"
                        + "- status: " + nullToEmpty(record.getStatus()) + "\n"
                        + "- user: " + compact(record.getUserInput(), 1000) + "\n"
                        + "- assistant: " + compact(record.getAgentOutput(), 1600))
                .collect(Collectors.joining("\n\n"));
    }

    private LambdaQueryWrapper<AgentExecutionMemory> buildQueryWrapper(String sessionId, String status, String keyword) {
        LambdaQueryWrapper<AgentExecutionMemory> wrapper = new LambdaQueryWrapper<>();
        if (hasText(sessionId)) {
            wrapper.eq(AgentExecutionMemory::getSessionId, sessionId.trim());
        }
        if (hasText(status) && !"all".equalsIgnoreCase(status.trim())) {
            wrapper.eq(AgentExecutionMemory::getStatus, status.trim());
        }
        if (hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(query -> query
                    .like(AgentExecutionMemory::getExecutionId, value)
                    .or()
                    .like(AgentExecutionMemory::getSessionId, value)
                    .or()
                    .like(AgentExecutionMemory::getUserInput, value)
                    .or()
                    .like(AgentExecutionMemory::getAgentOutput, value));
        }
        return wrapper;
    }

    private AgentExecutionMemory getEntity(String executionId) {
        if (!hasText(executionId)) {
            throw new IllegalArgumentException("executionId 不能为空");
        }
        AgentExecutionMemory entity = memoryMapper.selectOne(new LambdaQueryWrapper<AgentExecutionMemory>()
                .eq(AgentExecutionMemory::getExecutionId, executionId)
                .last("limit 1"));
        if (entity == null) {
            throw new NoSuchElementException("短期记忆不存在: " + executionId);
        }
        return entity;
    }

    private void applyRequest(AgentExecutionMemory entity, AgentExecutionMemoryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        entity.setSessionId(request.getSessionId());
        entity.setUserInput(request.getUserInput());
        entity.setAgentOutput(request.getAgentOutput());
        entity.setMemoryIndexSnapshot(request.getMemoryIndexSnapshot());
        entity.setShortMemorySnapshot(request.getShortMemorySnapshot());
        entity.setStatus(request.getStatus());
        entity.setErrorMessage(request.getErrorMessage());
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String compact(String value, int maxLength) {
        String normalized = nullToEmpty(value).trim().replaceAll("\\s+", " ");
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
