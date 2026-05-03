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
@TableName("scheduled_diagnostic_task")
public class ScheduledDiagnosticTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scheduleId;

    private String taskName;

    private Integer enabled;

    private String userQuestion;

    private String promptTemplate;

    private Integer intervalMinutes;

    private LocalDateTime nextRunAt;

    private LocalDateTime lastRunAt;

    private String lastTaskId;

    private String lastStatus;

    private String lastResultSummary;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
