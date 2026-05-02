package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("tool_config")
public class ToolConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolName;

    private Integer enabled;

    private LocalDateTime updatedAt;
}
