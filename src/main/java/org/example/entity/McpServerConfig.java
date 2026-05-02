package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("mcp_server_config")
public class McpServerConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String serverName;

    private String transportType;

    private String baseUrl;

    private String endpoint;

    private String headersJson;

    private Integer requestTimeoutSeconds;

    private Integer enabled;

    private String status;

    private String lastError;

    private LocalDateTime lastConnectedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
