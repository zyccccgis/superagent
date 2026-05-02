package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("agent_trace_step")
public class AgentTraceStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private String stepType;

    private String stepName;

    private String status;

    private String inputSummary;

    private String outputSummary;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private String errorMessage;

    private LocalDateTime createdAt;
}
