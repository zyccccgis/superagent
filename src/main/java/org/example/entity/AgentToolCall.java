package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("agent_tool_call")
public class AgentToolCall {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long stepId;

    private String toolName;

    private String toolSource;

    private String requestJson;

    private String responseSummary;

    private String status;

    private Long durationMs;

    private String errorMessage;

    private LocalDateTime createdAt;
}
