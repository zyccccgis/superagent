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
@TableName("agent_diagnostic_task")
public class AgentDiagnosticTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private String taskType;

    private String sceneCode;

    private String status;

    private String triggerSource;

    private String sessionId;

    private String requestPayload;

    private String inputSummary;

    private String plannerOutput;

    private String finalReport;

    private String errorCode;

    private String errorMessage;

    private String modelName;

    private String toolSummary;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
