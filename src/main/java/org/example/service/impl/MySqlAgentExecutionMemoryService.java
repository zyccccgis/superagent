package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.AgentExecutionMemoryListResponse;
import org.example.dto.AgentExecutionMemoryRequest;
import org.example.dto.AgentExecutionMemoryResponse;
import org.example.dto.MemoryContext;
import org.example.entity.AgentExecutionMemory;
import org.example.mapper.AgentExecutionMemoryMapper;
import org.example.service.AgentExecutionMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public MySqlAgentExecutionMemoryService(AgentExecutionMemoryMapper memoryMapper,
                                            AsyncTaskExecutor chatTaskExecutor) {
        this.memoryMapper = memoryMapper;
        this.chatTaskExecutor = chatTaskExecutor;
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
        } catch (Exception e) {
            logger.warn("异步记录 Agent 执行记忆失败: {}", e.getMessage(), e);
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
        LambdaQueryWrapper<AgentExecutionMemory> countWrapper = buildQueryWrapper(sessionId, status, keyword);
        Long total = memoryMapper.selectCount(countWrapper);
        int offset = (resolvedPage - 1) * resolvedPageSize;
        LambdaQueryWrapper<AgentExecutionMemory> pageWrapper = buildQueryWrapper(sessionId, status, keyword)
                .orderByDesc(AgentExecutionMemory::getCreatedAt)
                .last("limit " + resolvedPageSize + " offset " + offset);
        List<AgentExecutionMemory> records = memoryMapper.selectList(pageWrapper);

        AgentExecutionMemoryListResponse response = new AgentExecutionMemoryListResponse();
        response.setItems(records.stream()
                .map(AgentExecutionMemoryResponse::fromEntity)
                .toList());
        response.setTotal(total == null ? 0 : total);
        response.setPage(resolvedPage);
        response.setPageSize(resolvedPageSize);
        return response;
    }

    @Override
    public AgentExecutionMemoryResponse get(String executionId) {
        return AgentExecutionMemoryResponse.fromEntity(getEntity(executionId));
    }

    @Override
    public AgentExecutionMemoryResponse create(AgentExecutionMemoryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        AgentExecutionMemory entity = new AgentExecutionMemory();
        entity.setExecutionId(hasText(request.getExecutionId()) ? request.getExecutionId() : "manual_" + UUID.randomUUID().toString().replace("-", ""));
        applyRequest(entity, request);
        entity.setStatus(hasText(entity.getStatus()) ? entity.getStatus() : "MANUAL");
        entity.setStartedAt(now);
        entity.setFinishedAt(now);
        entity.setDurationMs(0L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        memoryMapper.insert(entity);
        return AgentExecutionMemoryResponse.fromEntity(entity);
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
