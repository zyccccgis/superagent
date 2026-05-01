package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("agent_execution_memory")
public class AgentExecutionMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String executionId;

    private String sessionId;

    private String userInput;

    private String agentOutput;

    private String memoryIndexSnapshot;

    private String shortMemorySnapshot;

    private String status;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
